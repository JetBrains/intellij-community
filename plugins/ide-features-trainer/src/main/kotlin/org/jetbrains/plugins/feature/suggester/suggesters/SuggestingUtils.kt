package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.featureStatistics.ProductivityFeaturesRegistry
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.PyNamedParameter
import com.jetbrains.python.psi.PyReferenceExpression
import com.jetbrains.python.psi.PyTargetExpression
import org.jetbrains.plugins.feature.suggester.FeatureUsageSuggestion
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

data class Selection(val startOffset: Int, val endOffset: Int, val text: String)

internal fun Editor.getSelection(): Selection? {
    with(selectionModel) {
        return if (selectedText != null) {
            Selection(selectionStart, selectionEnd, selectedText!!)
        } else {
            null
        }
    }
}

internal fun isCommentAddedToLineStart(file: PsiFile, offset: Int): Boolean {
    val fileBeforeCommentText = file.text.substring(0, offset)
    return fileBeforeCommentText.substringAfterLast('\n', fileBeforeCommentText).all { it == ' ' }
}

internal fun PsiElement.isOneLineComment(): Boolean {
    return this is PsiComment
            && text.startsWith("//")
            && text.substring(2).trim().isNotEmpty()
}

internal inline fun <reified T : PsiElement> PsiElement.getParentOfType(): T? {
    return PsiTreeUtil.getParentOfType(this, T::class.java)
}

internal fun PsiElement.isDeclaration(): Boolean {
    return this is PsiLocalVariable || this is PsiParameter || this is PsiField || this is PsiMethod
            || this is PyTargetExpression || this is PyNamedParameter || this is PyFunction
}

internal fun PsiElement.isIdentifier(): Boolean {
    return this is PsiIdentifier || (parent != null && (parent is PyReferenceExpression || parent.isDeclaration()))
}

internal fun PsiElement.resolveRef(): PsiElement? {
    return reference?.resolve()
}

internal fun Transferable.asString(): String? {
    return try {
        getTransferData(DataFlavor.stringFlavor) as? String
    } catch (ex: Exception) {
        null
    }
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