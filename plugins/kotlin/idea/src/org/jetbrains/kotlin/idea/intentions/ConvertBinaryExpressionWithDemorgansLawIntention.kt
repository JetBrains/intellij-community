// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Companion.invertSelectorFunction
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConvertBinaryExpressionWithDemorgansLawIntention : SelfTargetingOffsetIndependentIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.lazyMessage("demorgan.law")
) {
    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val expr = element.topmostBinaryExpression()

        setTextGetter(
            when (expr.operationToken) {
                KtTokens.ANDAND -> KotlinBundle.lazyMessage("replace.with2")
                KtTokens.OROR -> KotlinBundle.lazyMessage("replace.with")
                else -> return false
            }
        )

        return splitBooleanSequence(expr) != null
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) = applyTo(element)

    companion object {
        fun convertIfPossible(element: KtBinaryExpression) {
            val expr = element.topmostBinaryExpression()
            if (splitBooleanSequence(expr) == null) return
            applyTo(element)
        }

        private fun KtBinaryExpression.topmostBinaryExpression(): KtBinaryExpression =
            parentsWithSelf.takeWhile { it is KtBinaryExpression }.last() as KtBinaryExpression

        private fun applyTo(element: KtBinaryExpression) {
            val expr = element.topmostBinaryExpression()

            val operatorText = when (expr.operationToken) {
                KtTokens.ANDAND -> KtTokens.OROR.value
                KtTokens.OROR -> KtTokens.ANDAND.value
                else -> throw IllegalArgumentException()
            }

            val context by lazy { expr.analyze(BodyResolveMode.PARTIAL) }
            val operands = splitBooleanSequence(expr, context)?.asReversed() ?: return

            val newExpression = KtPsiFactory(expr).buildExpression {
                val negatedOperands = operands.map {
                    it.safeAs<KtQualifiedExpression>()?.invertSelectorFunction(context) ?: it.negate()
                }
                appendExpressions(negatedOperands, separator = operatorText)
            }

            val grandParentPrefix = expr.parent.parent as? KtPrefixExpression
            val negated = expr.parent is KtParenthesizedExpression &&
                    grandParentPrefix?.operationReference?.getReferencedNameElementType() == KtTokens.EXCL
            if (negated) {
                grandParentPrefix?.replace(newExpression)
            } else {
                expr.replace(newExpression.negate())
            }
        }

        private fun splitBooleanSequence(
            expression: KtBinaryExpression,
            context: BindingContext = expression.analyze(BodyResolveMode.PARTIAL)
        ): List<KtExpression>? {
            if (!expression.left.isBoolean(context) || !expression.right.isBoolean(context)) return null

            val result = ArrayList<KtExpression>()
            val firstOperator = expression.operationToken

            var remainingExpression: KtExpression = expression
            while (true) {
                if (remainingExpression !is KtBinaryExpression) break

                val operation = remainingExpression.operationToken
                if (operation != KtTokens.ANDAND && operation != KtTokens.OROR) break

                if (operation != firstOperator) return null //Boolean sequence must be homogenous

                result.add(remainingExpression.right ?: return null)
                remainingExpression = remainingExpression.left ?: return null
            }

            result.add(remainingExpression)
            return result
        }

        private fun KtExpression?.isBoolean(context: BindingContext) = this != null && context.getType(this)?.isBoolean() == true
    }
}
