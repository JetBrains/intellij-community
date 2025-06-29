// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.*

class JoinWhenEntryHandler : JoinRawLinesHandlerDelegate {
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN
        val element = file.findElementAt(start) ?: return CANNOT_JOIN

        val entry = element.getPrevSiblingIgnoringWhitespaceAndComments() as? KtWhenEntry ?: return CANNOT_JOIN
        val entryLastCondition = entry.conditions.lastOrNull() ?: return CANNOT_JOIN
        val whenExpression = entry.parent as? KtWhenExpression ?: return CANNOT_JOIN

        val prevSiblingIgnoringWhitespace = element.getPrevSiblingIgnoringWhitespace()
        val nextSiblingIgnoringWhitespace = element.getNextSiblingIgnoringWhitespace()

        if (nextSiblingIgnoringWhitespace is PsiComment && nextSiblingIgnoringWhitespace.noKtWhenEntryTillNewLine())
            return CANNOT_JOIN

        val nextEntry = entry.getNextSiblingIgnoringWhitespaceAndComments() as? KtWhenEntry ?: return CANNOT_JOIN

        if (nextEntry.isElse) return CANNOT_JOIN

        if (element is PsiWhiteSpace && element.text.contains('\n') && element.noKtWhenEntryTillNewLine(false))
            return CANNOT_JOIN

        if (entry.hasComments() || nextEntry.hasComments() || !nextEntry.hasSameExpression(entry)) {
            return joinWithSemicolon(document,
                                     prevSiblingIgnoringWhitespace ?: entry,
                                     nextSiblingIgnoringWhitespace ?: nextEntry)
        }

        val nextEntryFirstCondition = nextEntry.conditions.firstOrNull() ?: return CANNOT_JOIN
        val separator = if (whenExpression.subjectExpression != null) ", " else " || "
        document.replaceString(entryLastCondition.endOffset, nextEntryFirstCondition.startOffset, separator)
        return entry.startOffset
    }

    private fun joinWithSemicolon(
        document: Document,
        entry: PsiElement,
        nextEntry: PsiElement
    ): Int {
        document.replaceString(entry.textRange.endOffset, nextEntry.textRange.startOffset, "; ")
        return entry.textRange.endOffset + 1
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = CANNOT_JOIN

    private fun KtWhenEntry.hasComments(): Boolean =
        allChildren.any { it is PsiComment } || siblings(withItself = false).takeWhile { !it.textContains('\n') }.any { it is PsiComment }

    private fun KtWhenEntry.hasSameExpression(other: KtWhenEntry) = expression?.text == other.expression?.text

    private fun PsiElement.noKtWhenEntryTillNewLine(forward: Boolean = true) =
        siblings(forward = forward, withItself = false).takeWhile { !it.textContains('\n') }.none { it is KtWhenEntry }
}
