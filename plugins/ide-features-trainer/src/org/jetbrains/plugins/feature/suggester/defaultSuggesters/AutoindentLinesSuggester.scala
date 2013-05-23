package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, Suggestion, FeatureSuggester}
import org.jetbrains.plugins.feature.suggester.changes.{ChildReplacedAction, UserAnAction, UserAction}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiWhiteSpace

/**
 * @author Alefas
 * @since 25.05.13
 */
class AutoindentLinesSuggester extends FeatureSuggester {
  val POPUP_MESSAGE = "Why not to use autoindent feature (Ctrl + Shift + I)"

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    val command = CommandProcessor.getInstance.getCurrentCommand
    if (command != null) return NoSuggestion
    actions.last match {
      case ChildReplacedAction(parent, child: PsiWhiteSpace, oldChild: PsiWhiteSpace) if child.getText.contains("\n") &&
        child.getText.count(_ == '\n') == oldChild.getText.count(_ == '\n') &&
        child.getText.reverseIterator.takeWhile(_ != '\n').length != oldChild.getText.reverseIterator.takeWhile(_ != '\n').length &&
        child.getNextSibling != null && !child.getNextSibling.isInstanceOf[PsiWhiteSpace] =>
        return SuggestingUtil.createSuggestion(None, POPUP_MESSAGE)
      case _ =>
    }

    NoSuggestion
  }

  def getId: String = "Autoindent lines suggseter"
}
