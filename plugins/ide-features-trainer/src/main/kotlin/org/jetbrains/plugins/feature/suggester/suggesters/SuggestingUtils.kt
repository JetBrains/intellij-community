package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.command.CommandProcessor
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.feature.suggester.FeatureUsageSuggestion
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion

internal fun isCommentAddedToLineStart(file: PsiFile, offset: Int): Boolean {
    val fileBeforeCommentText = file.text.substring(0, offset)
    return fileBeforeCommentText.substringAfterLast('\n', fileBeforeCommentText).all { it == ' ' }
}

internal fun PsiElement.isOneLineComment(): Boolean {
    return this is PsiComment
            && text.startsWith("//")
            && text.substring(2).trim().isNotEmpty()
}

internal fun createSuggestion(descriptorId: String?, popupMessage: String, usageDelta: Long = 1000): Suggestion {
    val commandName = CommandProcessor.getInstance().currentCommandName
    if (commandName != null && (commandName.startsWith("Redo") || commandName.startsWith("Undo"))) {
        return NoSuggestion
    }
    if (descriptorId != null) {
        val descriptor = ProductivityFeaturesRegistry.getInstance()!!.getFeatureDescriptor(descriptorId)
        val lastTimeUsed = descriptor.lastTimeUsed
        val delta = System.currentTimeMillis() - lastTimeUsed
        if (delta < usageDelta) {
            return FeatureUsageSuggestion
        }
    }

    return PopupSuggestion(popupMessage)
}