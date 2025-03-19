// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Originally from IDEA platform: StatementGroupSelectioner
 */
class KotlinStatementGroupSelectioner : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement): Boolean {
        if (e !is KtExpression && e !is KtWhenEntry && e !is KtParameterList && e !is PsiComment) return false
        val parent = e.parent
        return parent.elementType == KtNodeTypes.BLOCK || parent is KtWhenExpression || parent is KtFunctionLiteral
    }

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
        val parent = e.parent

        val startElement = e.siblings(forward = false, withItself = false)
            .firstOrNull {
                // find preceding '{' or blank line
                it is LeafPsiElement && it.elementType == KtTokens.LBRACE || it is PsiWhiteSpace && it.getText()!!.count { it == '\n' } > 1
                        || (it is LeafPsiElement && it.elementType == KtTokens.ARROW && e.getLineNumber(editor) != it.getLineNumber(editor))
            }
            ?.siblings(forward = true, withItself = false)
            ?.dropWhile { it is PsiWhiteSpace } // and take first non-whitespace element after it
            ?.firstOrNull() ?: parent.firstChild!!

        val endElement = e.siblings(forward = true, withItself = false)
            .firstOrNull {
                // find next '}' or blank line
                it is LeafPsiElement && it.elementType == KtTokens.RBRACE || it is PsiWhiteSpace && it.getText()!!.count { it == '\n' } > 1
            }
            ?.siblings(forward = false, withItself = false)
            ?.dropWhile { it is PsiWhiteSpace } // and take first non-whitespace element before it
            ?.firstOrNull() ?: parent.lastChild!!

        return expandToWholeLine(
            editorText,
            TextRange(
                startElement.textRange!!.startOffset,
                endElement.textRange!!.endOffset
            )
        )
    }

    private fun PsiElement.getLineNumber(editor: Editor): Int {
        val index = textRange.startOffset
        val document = editor.document
        return if (index > document.textLength) 0 else document.getLineNumber(index)
    }
}
