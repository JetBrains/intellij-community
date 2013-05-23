package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, Suggestion, FeatureSuggester}
import org.jetbrains.plugins.feature.suggester.changes.{UserAnAction, ChildRemovedAction, UserAction}
import com.intellij.psi.{PsiField, PsiClass, PsiMethod}
import com.intellij.openapi.command.CommandProcessor
import com.intellij.ide.{IdeTooltipManager, ClipboardSynchronizer}
import java.awt.datatransfer.DataFlavor

/**
 * @author Alefas
 * @since 24.05.13
 */
class SafeDeleteSuggester extends FeatureSuggester {
  val POPUP_MESSAGE = "Why not to try Safe Delete action (Alt + Delete)"

  private var lastTimeForPopup = 0L

  def getSuggestion(actions: List[UserAction], anActions: List[UserAnAction]): Suggestion = {
    val name = CommandProcessor.getInstance().getCurrentCommandName
    if (name != null) return NoSuggestion //it's not user typing action, so let's do nothing

    actions.last match {
      case ChildRemovedAction(parent, child) =>
        child match {
          case _: PsiMethod | _: PsiClass | _: PsiField =>
            val contents = ClipboardSynchronizer.getInstance().getContents
            if (contents != null) {
              val delta = System.currentTimeMillis() - lastTimeForPopup
              if (delta < 50) {
                IdeTooltipManager.getInstance().hideCurrentNow(false)
                return NoSuggestion //do not suggest in case of deletion for few things
              }
              try {
                val clipboardContent = contents.getTransferData(DataFlavor.stringFlavor).asInstanceOf[String]
                if (clipboardContent.contains(child.getText)) return NoSuggestion //this is action with copy to clipboard side effect, so we shouldn't suggest anything
              }
              catch {
                case ignore: Exception =>
              }
              lastTimeForPopup = System.currentTimeMillis()
              return SuggestingUtil.createSuggestion(None, POPUP_MESSAGE)
            }
          case _ =>
        }
      case _ =>
    }
    NoSuggestion
  }

  def getId: String = "Safe delete suggester"
}
