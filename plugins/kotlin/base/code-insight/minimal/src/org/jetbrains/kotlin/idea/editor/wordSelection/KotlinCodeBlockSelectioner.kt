// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.KtNodeTypes
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

/**
 * Originally from IDEA platform: CodeBlockOrInitializerSelectioner
 */
class KotlinCodeBlockSelectioner : ExtendWordSelectionHandlerBase() {

    companion object {
        fun canSelect(e: PsiElement) = isTarget(e) || (isBrace(e) && isTarget(e.parent))

        private fun isTarget(e: PsiElement) = e.elementType == KtNodeTypes.BLOCK || e is KtWhenExpression

        private fun isBrace(e: PsiElement): Boolean {
            val elementType = e.node.elementType
            return elementType == KtTokens.LBRACE || elementType == KtTokens.RBRACE
        }
    }

    override fun canSelect(e: PsiElement) = KotlinCodeBlockSelectioner.canSelect(e)

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
        val result = ArrayList<TextRange>()

        val block = if (isBrace(e)) e.parent else e
        val start = findBlockContentStart(block)
        val end = findBlockContentEnd(block)
        if (end > start) {
            result.addAll(expandToWholeLine(editorText, TextRange(start, end)))
        }
        result.addAll(expandToWholeLine(editorText, block.textRange!!))

        return result
    }

    private fun findBlockContentStart(block: PsiElement): Int {
        val element = block.allChildren
            .dropWhile { it.node.elementType != KtTokens.LBRACE } // search for '{'
            .drop(1) // skip it
            .dropWhile { it is PsiWhiteSpace } // and skip all whitespaces
            .firstOrNull() ?: block
        return element.startOffset
    }

    private fun findBlockContentEnd(block: PsiElement): Int {
        val element = block.allChildren
            .toList()
            .asReversed()
            .asSequence()
            .dropWhile { it.node.elementType != KtTokens.RBRACE } // search for '}'
            .drop(1) // skip it
            .dropWhile { it is PsiWhiteSpace } // and skip all whitespaces
            .firstOrNull() ?: block
        return element.endOffset
    }
}
