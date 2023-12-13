// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtParameterList
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeParameterList
import org.jetbrains.kotlin.psi.KtValueArgumentList

class KotlinListSelectioner : ExtendWordSelectionHandlerBase() {
    companion object {
        fun canSelect(e: PsiElement) =
            e is KtParameterList || e is KtValueArgumentList || e is KtTypeParameterList || e is KtTypeArgumentList
    }

    override fun canSelect(e: PsiElement) = KotlinListSelectioner.canSelect(e)

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        val node = e.node!!
        val startNode = node.findChildByType(TokenSet.create(KtTokens.LPAR, KtTokens.LT)) ?: return null
        val endNode = node.findChildByType(TokenSet.create(KtTokens.RPAR, KtTokens.GT)) ?: return null
        val innerRange = TextRange(startNode.startOffset + 1, endNode.startOffset)
        if (e is KtTypeArgumentList || e is KtTypeParameterList) {
            return listOf(
                innerRange,
                TextRange(startNode.startOffset, endNode.startOffset + endNode.textLength)
            )
        }
        return listOf(innerRange)
    }
}
