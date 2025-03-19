// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.editor.wordSelection

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinCallExpressionWithLambdaSelectioner : ExtendWordSelectionHandlerBase() {

    override fun canSelect(e: PsiElement): Boolean = e is KtCallExpression && e.hasLambda()

    override fun select(e: PsiElement, editorText: CharSequence, cursorOffset: Int, editor: Editor): List<TextRange>? {
        if (e !is KtCallExpression) return null

        val endOffset = e.valueArgumentList?.endOffset ?: return null
        return listOf(TextRange(e.startOffset, endOffset))
    }

    private fun KtCallExpression.hasLambda(): Boolean = lambdaArguments.isNotEmpty()

}