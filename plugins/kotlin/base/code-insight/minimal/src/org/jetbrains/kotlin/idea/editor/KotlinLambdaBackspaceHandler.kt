package org.jetbrains.kotlin.idea.editor

import com.intellij.codeInsight.editorActions.BackspaceHandlerDelegate
import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinLambdaBackspaceHandler : BackspaceHandlerDelegate() {
    private var deleteBrace = false

    override fun beforeCharDeleted(c: Char, file: PsiFile, editor: Editor) {
        val offset = editor.getCaretModel().offset - 1
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

        val chars = document.charsSequence
        var rbraceOffset = offset
        while (rbraceOffset < chars.length && Character.isWhitespace(chars[rbraceOffset])) {
            rbraceOffset++
        }
        if (chars[rbraceOffset] != '}') {
            return false
        }

        val afterRbrace = rbraceOffset + 1

        val fileType = file.getFileType()
        val iterator = editor.highlighter.createIterator(rbraceOffset)

        val rparenOffset = BraceMatchingUtil.findRightmostRParen(iterator, iterator.getTokenType(), chars, fileType)

        var wsStart = offset - 1
        while (wsStart >= 0 && Character.isWhitespace(chars[wsStart])) {
            wsStart--
        }

        wsStart++
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