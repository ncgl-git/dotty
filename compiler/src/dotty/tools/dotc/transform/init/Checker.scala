package dotty.tools.dotc
package transform
package init

import dotty.tools.dotc._
import ast.tpd
import tpd._

import dotty.tools.dotc.core._
import Contexts._
import Types._
import Symbols._
import StdNames._

import dotty.tools.dotc.transform._
import Phases._

import scala.collection.mutable

import Semantic._
import dotty.tools.unsupported

class Checker extends Phase:

  override def phaseName: String = Checker.name

  override def description: String = Checker.description

  override val runsAfter = Set(Pickler.name)

  override def isEnabled(using Context): Boolean =
    super.isEnabled && (ctx.settings.YcheckInit.value || ctx.settings.YcheckInitGlobal.value)

  def traverse(traverser: InitTreeTraverser)(using Context): Boolean = monitor(phaseName):
    val unit = ctx.compilationUnit
    traverser.traverse(unit.tpdTree)

  override def runOn(units: List[CompilationUnit])(using Context): List[CompilationUnit] =
    val checkCtx = ctx.fresh.setPhase(this.start)
    val traverser = new InitTreeTraverser()
    val unitContexts = units.map(unit => checkCtx.fresh.setCompilationUnit(unit))

    val units0 =
      for unitContext <- unitContexts if traverse(traverser)(using unitContext) yield unitContext.compilationUnit

    cancellable {
      val classes = traverser.getClasses()

      if ctx.settings.YcheckInit.value then
        Semantic.checkClasses(classes)(using checkCtx)

      if ctx.settings.YcheckInitGlobal.value then
        Objects.checkClasses(classes)(using checkCtx)
    }

    units0
  end runOn

  def run(using Context): Unit = unsupported("run")

  class InitTreeTraverser extends TreeTraverser:
    private val classes: mutable.ArrayBuffer[ClassSymbol] = new mutable.ArrayBuffer

    def getClasses(): List[ClassSymbol] = classes.toList

    override def traverse(tree: Tree)(using Context): Unit =
      traverseChildren(tree)
      tree match {
        case mdef: MemberDef =>
          // self-type annotation ValDef has no symbol
          if mdef.name != nme.WILDCARD then
            mdef.symbol.defTree = tree

          mdef match
          case tdef: TypeDef if tdef.isClassDef =>
            val cls = tdef.symbol.asClass
            classes.append(cls)
          case _ =>

        case _ =>
      }
  end InitTreeTraverser

object Checker:
  val name: String = "initChecker"
  val description: String = "check initialization of objects"
