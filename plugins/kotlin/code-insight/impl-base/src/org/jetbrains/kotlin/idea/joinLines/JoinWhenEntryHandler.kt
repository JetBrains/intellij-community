// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtWhenEntry
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.*

class JoinWhenEntryHandler : JoinRawLinesHandlerDelegate {
    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (file !is KtFile) return CANNOT_JOIN
        val element = file.findElementAt(start) ?: return CANNOT_JOIN

        val entry = element.getPrevSiblingIgnoringWhitespaceAndComments() as? KtWhenEntry ?: return CANNOT_JOIN
        if (entry.hasComments()) return CANNOT_JOIN
        val entryLastCondition = entry.conditions.lastOrNull() ?: return CANNOT_JOIN
        val whenExpression = entry.parent as? KtWhenExpression ?: return CANNOT_JOIN

        val nextEntry = entry.getNextSiblingIgnoringWhitespaceAndComments() as? KtWhenEntry ?: return CANNOT_JOIN
        if (nextEntry.isElse || nextEntry.hasComments() || !nextEntry.hasSameExpression(entry)) return CANNOT_JOIN
        val nextEntryFirstCondition = nextEntry.conditions.firstOrNull() ?: return CANNOT_JOIN

        val separator = if (whenExpression.subjectExpression != null) ", " else " || "
        document.replaceString(entryLastCondition.endOffset, nextEntryFirstCondition.startOffset, separator)
        return entry.startOffset
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int = CANNOT_JOIN

    private fun KtWhenEntry.hasComments(): Boolean =
        allChildren.any { it is PsiComment } || siblings(withItself = false).takeWhile { !it.textContains('\n') }.any { it is PsiComment }

    private fun KtWhenEntry.hasSameExpression(other: KtWhenEntry) = expression?.text == other.expression?.text
}
