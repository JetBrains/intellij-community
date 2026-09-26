// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithRangeAdjuster
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionLiteral
import org.jetbrains.kotlin.psi.KtLambdaArgument
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset

class KotlinSurroundWithRangeAdjuster : SurroundWithRangeAdjuster {

    override fun adjustSurroundWithRange(file: PsiFile, selectedRange: TextRange): TextRange = selectedRange

    override fun adjustSurroundWithRange(file: PsiFile, selectedRange: TextRange, hasSelection: Boolean): TextRange {
        if (hasSelection) return selectedRange
        if (file !is KtFile) return selectedRange

        val startOffset = selectedRange.startOffset
        val endOffset = selectedRange.endOffset
        if (startOffset >= endOffset) return selectedRange

        val document = file.viewProvider.document ?: return selectedRange
        val leaf = file.findElementAt(endOffset - 1) ?: return selectedRange

        var current: PsiElement? = leaf
        while (current != null && current !is KtFile) {
            if (current.endOffset != endOffset) break

            if (current is KtExpression
                && current.startOffset <= startOffset
                && isApplicableSurroundTarget(current)
            ) {
                val newStart = current.startOffset
                if (newStart == startOffset) {
                    return selectedRange
                }
                val startLine = document.getLineNumber(newStart)
                val endLine = document.getLineNumber(endOffset)
                return if (startLine != endLine) TextRange(newStart, endOffset) else selectedRange
            }

            current = current.parent
        }

        return selectedRange
    }

    private fun isApplicableSurroundTarget(expression: KtExpression): Boolean {
        if (expression is KtBlockExpression) return false
        if (expression is KtFunctionLiteral) return false
        if (expression is KtLambdaExpression && expression.parent is KtLambdaArgument) return false
        if (expression is KtCallExpression && expression.parent is KtQualifiedExpression) return false
        return true
    }
}
