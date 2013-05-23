package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, Suggestion, FeatureSuggester}
import org.jetbrains.plugins.feature.suggester.changes._
import com.intellij.psi.{PsiErrorElement, PsiComment}
import com.intellij.openapi.command.CommandProcessor
import org.jetbrains.plugins.feature.suggester.changes.ChildRemovedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildReplacedAction
import org.jetbrains.plugins.feature.suggester.changes.ChildAddedAction
import org.jetbrains.plugins.feature.suggester.PopupSuggestion

/**
 * @author Alefas
 * @since 23.05.13
 */
class LineCommentingSuggester extends FeatureSuggester {
  import SuggestingUtil._

  val POPUP_MESSAGE = "Why no to use line commenting feature (Ctrl + Slash)"
  val UNCOMMENTING_POPUP_MESSAGE = "Why no to use line uncommenting feature (Ctrl + Slash)"
  val DESCRIPTOR_ID = "codeassists.comment.line"

  private var uncommentingActionStart: Option[UserAction] = None

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    val name = CommandProcessor.getInstance().getCurrentCommandName
    if (name != null) return NoSuggestion //it's not user typing action, so let's do nothing

    actions.last match {
      case ChildAddedAction(_, child: PsiComment) if child.getText.startsWith("//") && child.getText.substring(2).trim.nonEmpty =>
        if (checkCommentAddedToLineStart(child.getContainingFile, child.getTextRange.getStartOffset)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case ChildReplacedAction(_, child: PsiComment, oldChild) if child.getText.startsWith("//") &&
        child.getText.substring(2).trim.nonEmpty && !oldChild.isInstanceOf[PsiComment] =>
        if (checkCommentAddedToLineStart(child.getContainingFile, child.getTextRange.getStartOffset)) {
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), POPUP_MESSAGE)
        }
      case ChildReplacedAction(_, child, oldChild: PsiComment) if oldChild.getText.startsWith("//") &&
        oldChild.getText.substring(2).trim.nonEmpty && !child.isInstanceOf[PsiComment] =>
        val offset = child.getTextRange.getStartOffset
        if (checkCommentAddedToLineStart(child.getContainingFile, offset)) {
          val suggestion = SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), UNCOMMENTING_POPUP_MESSAGE)
          if (suggestion.isInstanceOf[PopupSuggestion]) {
            uncommentingActionStart = Some(actions.last)
          }
          return suggestion
        }
      case ChildRemovedAction(parent, child: PsiErrorElement) if child.getText == "/" && uncommentingActionStart.isDefined =>
        if (actions.contains(uncommentingActionStart.get)) {
          uncommentingActionStart = None
          return SuggestingUtil.createSuggestion(Some(DESCRIPTOR_ID), UNCOMMENTING_POPUP_MESSAGE)
        }
      case _ =>
    }
    NoSuggestion
  }

  def getId: String = "Commenting suggester"
}

object LineCommentingSuggester {

}
