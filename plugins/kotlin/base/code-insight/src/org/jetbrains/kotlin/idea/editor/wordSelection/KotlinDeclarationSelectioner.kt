// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDestructuringDeclaration
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinDeclarationSelectioner : ExtendWordSelectionHandlerBase() {
    override fun canSelect(e: PsiElement) = e is KtDeclaration

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange> {
        if (e is KtDestructuringDeclaration) {
            return selectMultiDeclaration(editorText, e)
        }

        val result = ArrayList<TextRange>()

        val firstChild = e.firstChild
        val firstComment = firstChild
            .siblings(forward = true, withItself = true)
            .firstOrNull { it is PsiComment }
        val firstNonComment = firstChild
            .siblings(forward = true, withItself = true)
            .first { it !is PsiComment && it !is PsiWhiteSpace }

        val lastChild = e.lastChild
        val lastComment = lastChild
            .siblings(forward = false, withItself = true)
            .firstOrNull { it is PsiComment }
        val lastNonComment = lastChild
            .siblings(forward = false, withItself = true)
            .first { it !is PsiComment && it !is PsiWhiteSpace }

        if (firstComment != null && cursorOffset <= firstComment.startOffset) {
            result.addRange(editorText, TextRange(firstComment.startOffset, firstComment.endOffset))
        }

        if (firstComment != null || lastComment != null) {
            val startOffset = minOf(
                firstComment?.startOffset ?: Int.MAX_VALUE,
                lastNonComment.startOffset
            )
            val endOffset = maxOf(
                lastComment?.endOffset ?: Int.MIN_VALUE,
                lastNonComment.endOffset
            )
            result.addRange(editorText, TextRange(startOffset, endOffset))
        }

        if (firstNonComment != firstChild || lastNonComment != lastChild) {
            result.addRange(editorText, TextRange(firstNonComment.startOffset, lastNonComment.endOffset))
        }

        result.addRange(editorText, e.textRange)

        return result
    }

    private fun selectMultiDeclaration(editorText: CharSequence, e: KtDestructuringDeclaration): ArrayList<TextRange> {
        val result = ArrayList<TextRange>()
        val lpar = e.lPar
        val rpar = e.rPar
        if (lpar != null && rpar != null) {
            result.addRange(editorText, TextRange(lpar.textRange.endOffset, rpar.textRange.startOffset))
            result.addRange(editorText, TextRange(lpar.textRange.startOffset, rpar.textRange.endOffset))
        }
        return result
    }
}

fun MutableList<TextRange>.addRange(editorText: CharSequence, range: TextRange) {
    addAll(ExtendWordSelectionHandlerBase.expandToWholeLine(editorText, range))
}
