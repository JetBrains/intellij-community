package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, Suggestion, FeatureSuggester}
import org.jetbrains.plugins.feature.suggester.changes.{UserAnAction, ChildAddedAction, ChildReplacedAction, UserAction}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.{PsiIdentifier, PsiAnnotation, PsiComment}
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl

/**
 * @author Alefas
 * @since 24.05.13
 */
class SuppressWarningSuggester extends FeatureSuggester {
  val POPUP_MESSAGE = "Why no to use quickfix for inspection to suppress it (Alt + Enter)"

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    val name = CommandProcessor.getInstance().getCurrentCommandName
    val phase = CompletionServiceImpl.getCompletionPhase
    if (phase != null) {
      val indicator = phase.indicator
      if (indicator == null && name != null) return NoSuggestion
    } else if (name != null) return NoSuggestion

    actions.last match {
      case ChildReplacedAction(_, child: PsiComment, oldChild) if child.getText.startsWith("//noinspection") =>
        if (oldChild.isInstanceOf[PsiComment] && oldChild.getText.startsWith("//noinspection")) return NoSuggestion
        if (SuggestingUtil.checkCommentAddedToLineStart(child.getContainingFile, child.getTextRange.getStartOffset)) {
          return SuggestingUtil.createSuggestion(null, POPUP_MESSAGE)
        }
      case ChildAddedAction(_, child: PsiAnnotation) if child.getText.startsWith("@SuppressWarnings") =>
        return SuggestingUtil.createSuggestion(null, POPUP_MESSAGE)
      case ChildReplacedAction(_, child: PsiAnnotation, oldChild) if child.getText.startsWith("@SuppressWarnings") =>
        if (oldChild.isInstanceOf[PsiAnnotation] && oldChild.getText.startsWith("@SuppressWarnings")) return NoSuggestion
        return SuggestingUtil.createSuggestion(null, POPUP_MESSAGE)
      case ChildAddedAction(_, child: PsiIdentifier) if child.getText == "SuppressWarnings" =>
        return checkIdentifier(child)
      case ChildReplacedAction(_, child: PsiIdentifier, oldChild) if child.getText.startsWith("SuppressWarnings") =>
        return checkIdentifier(child)
      case _ =>
    }
    NoSuggestion
  }


  override def needToClearLookup(): Boolean = true

  private def checkIdentifier(child: PsiIdentifier): Suggestion = {
    val parent = child.getParent
    parent match {
      case null => return NoSuggestion
      case _: PsiAnnotation =>
      case _ =>
        parent.getParent match {
          case null => return NoSuggestion
          case _: PsiAnnotation =>
          case _ => return NoSuggestion
        }
    }
    SuggestingUtil.createSuggestion(null, POPUP_MESSAGE)
  }

  def getId: String = "Suppress warning suggester"
}
