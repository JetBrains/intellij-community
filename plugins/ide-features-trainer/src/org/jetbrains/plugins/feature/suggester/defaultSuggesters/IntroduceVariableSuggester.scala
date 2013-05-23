package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, Suggestion, FeatureSuggester}
import org.jetbrains.plugins.feature.suggester.changes._
import com.intellij.psi._
import com.intellij.ide.ClipboardSynchronizer
import java.awt.datatransfer.DataFlavor
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.feature.suggester.changes.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction

/**
 * @author Alefas
 * @since 24.05.13
 */
class IntroduceVariableSuggester extends FeatureSuggester {
  val POPUP_MESSAGE = "Why not to use Introduce Variable refactoring (Ctrl + Alt + V)"
  val DESCRIPTOR_ID = "refactoring.introduceVariable"

  private case class Expr(exprText: String, method: PsiMethod)
  private var copiedExpression: Option[Expr] = None

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    actions.last match {
      case ChildRemovedAction(parent, child: PsiExpression) =>
        try {
          val contents = ClipboardSynchronizer.getInstance().getContents
          if (contents != null) {
            val clipboardContent = contents.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
            if (clipboardContent == child.getText) {
              //let's store this action
              val method: PsiMethod = PsiTreeUtil.getParentOfType(parent, classOf[PsiMethod], false)
              if (method == null) return NoSuggestion
              copiedExpression = Some(Expr(child.getText, method))
            }
          }
        }
        catch {
          case ignore: Exception =>
        }
      case ChildReplacedAction(parent, error: PsiErrorElement, child: PsiExpression) =>
        try {
          val contents = ClipboardSynchronizer.getInstance().getContents
          if (contents != null) {
            val clipboardContent = contents.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
            if (clipboardContent == child.getText) {
              //let's store this action
              val method: PsiMethod = PsiTreeUtil.getParentOfType(parent, classOf[PsiMethod], false)
              if (method == null) return NoSuggestion
              copiedExpression = Some(Expr(child.getText, method))
            }
          }
        }
        catch {
          case ignore: Exception =>
        }
      case ChildAddedAction(parent: PsiLocalVariable, expr: PsiExpression) =>
        if (checkLocalVariable(parent, expr)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case ChildReplacedAction(parent: PsiLocalVariable, expr: PsiExpression, _) =>
        if (checkLocalVariable(parent, expr)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case ChildReplacedAction(_, newLocal: PsiLocalVariable, oldLocal: PsiLocalVariable) =>
        if (newLocal.getName != oldLocal.getName) return NoSuggestion
        val initializer = newLocal.getInitializer
        if (initializer != null && checkLocalVariable(newLocal, initializer)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case ChildReplacedAction(_, decl1: PsiDeclarationStatement, decl2: PsiDeclarationStatement) =>
        val declaredElements = decl1.getDeclaredElements
        if (declaredElements.length != 1) return NoSuggestion
        val declaredElements2 = decl2.getDeclaredElements
        if (declaredElements2.length != 1) return NoSuggestion
        if (!declaredElements(0).isInstanceOf[PsiLocalVariable]) return NoSuggestion
        if (!declaredElements2(0).isInstanceOf[PsiLocalVariable]) return NoSuggestion
        val newLocal = declaredElements(0).asInstanceOf[PsiLocalVariable]
        val oldLocal = declaredElements2(0).asInstanceOf[PsiLocalVariable]
        if (newLocal.getName != oldLocal.getName) return NoSuggestion
        val initializer = newLocal.getInitializer
        if (initializer != null && checkLocalVariable(newLocal, initializer)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case _ =>
    }
    NoSuggestion
  }

  private def checkLocalVariable(parent: PsiLocalVariable, expr: PsiExpression): Boolean = {
    copiedExpression match {
      case Some(Expr(exprText, method)) =>
        if (!method.isValid) {
          copiedExpression = None
          return false
        }
        if (!PsiTreeUtil.isAncestor(method, parent, true)) return false
        if (expr.getText != exprText) return false
        true
      case _ => false
    }
  }

  def getId: String = "Introduce variable suggester"
}
