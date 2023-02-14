// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.kdoc

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.kdoc.lexer.KDocTokens
import org.jetbrains.kotlin.psi.KtFile

class KDocTypedHandler : TypedHandlerDelegate() {
    override fun beforeCharTyped(c: Char, project: Project, editor: Editor, file: PsiFile, fileType: FileType): Result {
        if (overwriteClosingBracket(c, editor, file)) {
            EditorModificationUtil.moveCaretRelatively(editor, 1)
            return Result.STOP
        }
        return Result.CONTINUE
    }

    override fun charTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Result =
        if (handleBracketTyped(c, project, editor, file)) Result.STOP else Result.CONTINUE

    private fun overwriteClosingBracket(c: Char, editor: Editor, file: PsiFile): Boolean {
        if (c != ']' && c != ')') return false
        if (file !is KtFile) return false
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false

        val offset = editor.caretModel.offset
        val document = editor.document
        val chars = document.charsSequence
        if (offset < document.textLength && chars[offset] == c) {
            val iterator = (editor as EditorEx).highlighter.createIterator(offset)
            val elementType = iterator.tokenType
            if (iterator.start == 0) return false
            iterator.retreat()
            val prevElementType = iterator.tokenType

            return when (c) {
                ']' -> {
                    // if the bracket is not part of a link, it will be part of KDOC_TEXT, not a separate RBRACKET element
                    prevElementType in KDocTokens.KDOC_HIGHLIGHT_TOKENS && (elementType == KDocTokens.MARKDOWN_LINK || (offset > 0 && chars[offset - 1] == '['))
                }

                ')' -> elementType == KDocTokens.MARKDOWN_INLINE_LINK

                else -> false
            }
        }
        return false
    }

    private fun handleBracketTyped(c: Char, project: Project, editor: Editor, file: PsiFile): Boolean {
        if (c != '[' && c != '(') return false
        if (file !is KtFile) return false
        if (!CodeInsightSettings.getInstance().AUTOINSERT_PAIR_BRACKET) return false

        val offset = editor.caretModel.offset
        if (offset == 0) return false

        val document = editor.document
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val element = file.findElementAt(offset - 1) ?: return false
        if (element.node.elementType != KDocTokens.TEXT) return false

        when (c) {
            '[' -> {
                document.insertString(offset, "]")
                return true
            }

            '(' -> {
                if (offset > 1 && document.charsSequence[offset - 2] == ']') {
                    document.insertString(offset, ")")
                    return true
                }
            }
        }
        return false
    }
}
