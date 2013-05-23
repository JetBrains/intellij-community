package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.{PsiBlockStatement, PsiExpression, PsiIfStatement}
import scala.collection.immutable.List
import org.jetbrains.plugins.feature.suggester.{NoSuggestion, FeatureSuggester, Suggestion}
import org.jetbrains.plugins.feature.suggester.changes._
import org.jetbrains.plugins.feature.suggester.changes.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction
import scala.Some

/**
 * @author Alefas
 * @since 24.05.13
 */
class RemoveElseActionSuggester extends FeatureSuggester {
  val REMOVE_ELSE_POPUP = "Why not to use Remove Else action (Ctrl + Shift + Delete)"

  private var waitForElseRemoving: Option[PsiIfStatement] = None

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    val command = CommandProcessor.getInstance.getCurrentCommand
    if (command != null) return NoSuggestion

    actions.last match {
      case ChildRemovedAction(parent: PsiIfStatement, _: PsiExpression | _: PsiBlockStatement) if parent.getThenBranch != null =>
        waitForElseRemoving = Some(parent)
        actions.reverseIterator.find {
          case ChildRemovedAction(additionalParent: PsiIfStatement, child) if child.getText == "else" =>
            additionalParent == parent
          case _ => false
        } match {
          case Some(_) => return SuggestingUtil.createSuggestion(None, REMOVE_ELSE_POPUP)
          case _ =>
        }
      case ChildRemovedAction(parent: PsiIfStatement, child) if child.getText == "else" =>
        waitForElseRemoving match {
          case Some(statement) if parent eq statement =>
            waitForElseRemoving = None
            return SuggestingUtil.createSuggestion(None, REMOVE_ELSE_POPUP)
          case _ =>
        }
      case ChildReplacedAction(_, n: PsiIfStatement, m: PsiIfStatement) if n.eq(m) && n.getElseBranch == null =>
        //todo: why this hack is required, there is no any special change in the following case:
        /**
         * {{{
         *   if (true) {
         *
         *   } else {
         *   }
         * }}}
         *
         * Else here is highlighted without additional whitespaces
         */
        val currentTime = System.currentTimeMillis()
        anActions.reverseIterator.find {
          case _: BackspaceAction => true
          case _ => false
        } match {
          case Some(BackspaceAction(text, timestamp)) if
            text.trim.startsWith("else") && currentTime - timestamp < 50 => return SuggestingUtil.createSuggestion(None, REMOVE_ELSE_POPUP)
          case _ =>
        }
      case _ =>
    }

    NoSuggestion
  }

  def getId: String = "Remove else action suggester"
}


