// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.joinLines

import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN
import com.intellij.codeInsight.editorActions.JoinRawLinesHandlerDelegate
import com.intellij.openapi.editor.Document
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.kdoc.psi.api.KDoc

class KDocJoinLinesHandler : JoinRawLinesHandlerDelegate {
    private fun couldBeJoined(
        file: PsiFile,
        start: Int,
        end: Int,
        document: Document
    ): Boolean {
        if (file.fileType !is KotlinFileType) return false
        if (start <= 0 || end >= document.textLength) return false

        val elementBefore = file.findElementAt(start - 1) ?: return false
        val elementAfter = file.findElementAt(end) ?: return false
        val kDocElementBefore = elementBefore.parentOfType<KDoc>(true)
        val kDocElementAfter = elementAfter.parentOfType<KDoc>(true)
        if (kDocElementBefore != kDocElementAfter) return false

        return true
    }

    override fun tryJoinRawLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (!couldBeJoined(file, start, end, document)) return CANNOT_JOIN

        val elementBefore = file.findElementAt(start - 1) ?: return CANNOT_JOIN
        if (elementBefore.node?.elementType != KDocTokens.START) return CANNOT_JOIN
        if (document.charsSequence[end] != '*') return CANNOT_JOIN
        if (end + 1 < document.textLength && document.charsSequence[end + 1] == '/') return CANNOT_JOIN

        return joinContentLine(document, start - 1, end)
    }

    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        if (!couldBeJoined(file, start, end, document)) return CANNOT_JOIN

        val elementBefore = file.findElementAt(start) ?: return CANNOT_JOIN
        val elementAfter = file.findElementAt(end) ?: return CANNOT_JOIN

        val contentAsteriskOffset = joinedContentAsteriskOffset(document, elementBefore, start)
        if (contentAsteriskOffset != null) {
            return joinContentLine(document, start, contentAsteriskOffset)
        }

        return when (elementAfter.node?.elementType) {
            KDocTokens.LEADING_ASTERISK -> joinContentLine(document, start, end)
            KDocTokens.END -> joinEndLine(document, start, end)
            else -> CANNOT_JOIN
        }
    }

    private fun joinContentLine(document: Document, start: Int, end: Int): Int {
        val contentOffset = skipSpacesAndTabsForward(document.charsSequence, end + 1)
        val caretOffset = replaceKDocLineBreak(document, start, contentOffset)
        collapseKDocEndIfSingleContentLine(document, caretOffset)
        return caretOffset
    }

    private fun joinEndLine(document: Document, start: Int, end: Int): Int =
      replaceKDocLineBreak(document, start, end)

    private fun replaceKDocLineBreak(document: Document, start: Int, end: Int): Int {
        document.replaceString(start + 1, end, " ")
        return start + 1
    }

    private fun joinedContentAsteriskOffset(document: Document, elementBefore: PsiElement, start: Int): Int? {
        if (elementBefore.node?.elementType != KDocTokens.START) return null

        val offset = skipSpacesAndTabsForward(document.charsSequence, start + 1)
        if (offset >= document.textLength || document.charsSequence[offset] != '*') return null
        if (offset + 1 < document.textLength && document.charsSequence[offset + 1] == '/') return null

        return offset
    }

    private fun collapseKDocEndIfSingleContentLine(document: Document, contentOffset: Int) {
        val lineNumber = document.getLineNumber(contentOffset)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        if (lineNumber == document.lineCount - 1) return

        val nextLineStartOffset = document.getLineStartOffset(lineNumber + 1)
        val kDocEndOffset = skipSpacesAndTabsForward(document.charsSequence, nextLineStartOffset)
        if (!document.charsSequence.startsWith("*/", kDocEndOffset)) return

        document.replaceString(lineEndOffset, kDocEndOffset, " ")
    }

    private fun skipSpacesAndTabsForward(text: CharSequence, offset: Int): Int {
        var currentOffset = offset
        while (currentOffset < text.length && text[currentOffset].let { it == ' ' || it == '\t' }) {
            currentOffset++
        }
        return currentOffset
    }
}
