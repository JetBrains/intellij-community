// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtFunctionLiteral

internal class KotlinLambdaBackspaceHandler : BackspaceHandlerDelegate() {
    private var deleteBrace = false

    private fun isLambdaLBrace(file: PsiFile, offset: Int): Boolean {
        val element = PsiUtilCore.getElementAtOffset(file, offset)
        return element.getNode().getElementType() === KtTokens.LBRACE && element.getParent() is KtFunctionLiteral
    }

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        val offset = editor.getCaretModel().offset - 1
        if (offset < 0) return

        deleteBrace = c == '{' && file.getFileType() === KotlinFileType.INSTANCE &&
                isLambdaLBrace(file, offset)
    }

    override fun charDeleted(c: Char, file: PsiFile, editor: Editor): Boolean {
        if (!deleteBrace || c != '{') {
            return false
        }

        val document = editor.getDocument()
        val offset = editor.getCaretModel().offset
        if (document.textLength <= offset) {
            return false
        }

        // From current position, skip whitespaces to the right until we hit the closing brace
        val chars = document.charsSequence
        var rbraceOffset = offset
        while (rbraceOffset < chars.length && Character.isWhitespace(chars[rbraceOffset])) {
            rbraceOffset++
        }

        // If it is not a closing brace, the function literal is not empty
        if (rbraceOffset >= chars.length || chars[rbraceOffset] != '}') {
            return false
        }

        val afterRbrace = rbraceOffset + 1

        val fileType = file.getFileType()

        // We also want to remove whitespace before the opening brace to return to the identifier/argument list
        var wsStart = offset - 1
        while (wsStart >= 0 && Character.isWhitespace(chars[wsStart])) {
            wsStart--
        }
        wsStart++

        // Find the rightmost closing brace following the current brace.
        // Below, we then trace back to find its corresponding opening brace.
        // If the braces were balanced (i.e., no missing braces), then the last closing brace should
        // no longer have a corresponding opening brace.
        // Otherwise, there was a missing brace, and we cannot be sure we would be removing the function literal.
        val iterator = editor.highlighter.createIterator(rbraceOffset)
        val rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, iterator.getTokenType(), chars, fileType)

        if (rparenOffset >= 0) {
            val iterator1 = editor.highlighter.createIterator(rparenOffset)
            if (BraceMatchingUtil.matchBrace(chars, fileType, iterator1, false, true)) {
                if (wsStart < offset) {
                    document.deleteString(wsStart, offset)
                }
                return true
            }
        }

        document.deleteString(wsStart, afterRbrace)
        return true
    }
}