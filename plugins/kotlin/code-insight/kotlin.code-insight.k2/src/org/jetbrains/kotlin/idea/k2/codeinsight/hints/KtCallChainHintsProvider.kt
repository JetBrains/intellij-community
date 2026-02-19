// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPostfixExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression

class KtCallChainHintsProvider : AbstractKtInlayHintsProvider() {
    private data class ExpressionWithType(val expression: KtExpression, val type: KaType)

    override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
        val topmostDotQualifiedExpression = (element as? KtQualifiedExpression)
            // We will process the whole chain using topmost DotQualifiedExpression.
            // If the current one has parent then it means that it's not topmost DotQualifiedExpression
            ?.takeIf { it.getParentDotQualifiedExpression() == null }
            ?: return

        analyze(topmostDotQualifiedExpression) {
            var someTypeIsUnknown = false
            val reversedChain =
                generateSequence<KtExpression>(topmostDotQualifiedExpression) {
                    (it.skipParenthesesAndPostfixOperatorsDown() as? KtQualifiedExpression)?.receiverExpression
                }
                    .drop(1) // Except last to avoid builder.build() which has obvious type
                    .filter { (it.nextSibling as? PsiWhiteSpace)?.textContains('\n') == true }
                    .map {
                        val ktType = it.expressionType
                        it to ktType
                    }
                    .takeWhile { (_, type) -> (type != null).also { if (!it) someTypeIsUnknown = true } }
                    .map { (expression, type) -> ExpressionWithType(expression, type!!) }
                    .windowed(2, partialWindows = true) { it.first() to it.getOrNull(1) }
                    .filter { (expressionWithType, prevExpressionWithType) ->
                        if (prevExpressionWithType == null) {
                            // Show type for expression in call chain on the first line only if it's dot qualified
                            expressionWithType.expression.skipParenthesesAndPostfixOperatorsDown() is KtQualifiedExpression
                        } else {
                            !expressionWithType.type.semanticallyEquals(prevExpressionWithType.type) ||
                                    prevExpressionWithType.expression.skipParenthesesAndPostfixOperatorsDown() !is KtQualifiedExpression
                        }
                    }
                    .map { it.first }
                    // Error types cannot be printed by `printKtType`, so we shouldn't include them in the chain.
                    .filter { it.type !is KaErrorType }
                    .toList()
            if (someTypeIsUnknown) return
            //if (isChainUnacceptable(reversedChain)) return

            if (reversedChain.asSequence().distinctBy { it.type }.count() < uniqueTypeCount) return

            for ((expression, type) in reversedChain) {
                sink.addPresentation(
                    InlineInlayPosition(expression.textRange.endOffset, relatedToPrevious = true),
                    hintFormat = HintFormat.default,
                ) {
                    printKtType(type)
                }
            }
        }
        return
    }

    private val uniqueTypeCount: Int
        get() = 2

    private fun KtQualifiedExpression.getParentDotQualifiedExpression(): KtQualifiedExpression? {
        var expression: PsiElement? = parent
        while (
            expression is KtPostfixExpression ||
            expression is KtParenthesizedExpression ||
            expression is KtArrayAccessExpression ||
            expression is KtCallExpression
        ) {
            expression = expression.parent
        }
        return expression as? KtQualifiedExpression
    }

    private fun PsiElement.skipParenthesesAndPostfixOperatorsDown(): PsiElement? {
        var expr: PsiElement? = this
        while (true) {
            expr = when (expr) {
                is KtPostfixExpression -> expr.baseExpression
                is KtParenthesizedExpression -> expr.expression
                is KtArrayAccessExpression -> expr.arrayExpression
                is KtCallExpression -> expr.calleeExpression
                else -> break
            }
        }
        return expr
    }
}