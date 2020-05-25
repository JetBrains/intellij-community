package org.jetbrains.plugins.feature.suggester.defaultSuggesters

import org.jetbrains.plugins.feature.suggester.{NoSuggestion, PopupSuggestion, FeatureUsageSuggestion, Suggestion}
import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiFile
import com.intellij.openapi.util.text.StringUtil

/**
 * @author Alefas
 * @since 23.05.13
 */
object SuggestingUtil {
  def createSuggestion(descriptorId: Option[String], popupMessage: String, usageDelta: Long = 1000): Suggestion = {
    val commandName = CommandProcessor.getInstance().getCurrentCommandName
    if (commandName != null && (commandName.startsWith("Redo") || commandName.startsWith("Undo"))) return NoSuggestion
    descriptorId match {
      case Some(descriptorId) =>
        val descriptor = ProductivityFeaturesRegistry.getInstance().getFeatureDescriptor(descriptorId)
        val lastTimeUsed = descriptor.getLastTimeUsed
        val delta = System.currentTimeMillis() - lastTimeUsed
        if (delta < usageDelta) return FeatureUsageSuggestion
      case _ =>
    }
    PopupSuggestion(popupMessage)
  }

  def checkCommentAddedToLineStart(file: PsiFile, offset: Int): Boolean = {
    val fileBeforeCommentText = file.getText.substring(0, offset)
    fileBeforeCommentText.reverseIterator.takeWhile(_ != '\n').forall(StringUtil.isWhiteSpace)
  }
}
