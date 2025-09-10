// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.idea.inspections.ReplaceNegatedIsEmptyWithIsNotEmptyInspection.Util.invertSelectorFunction
import org.jetbrains.kotlin.idea.intentions.ConvertBinaryExpressionWithDemorgansLawIntention.Holder.topmostBinaryExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiUtil.deparenthesize
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.typeUtil.isBoolean
import org.jetbrains.kotlin.util.match
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class ConvertBinaryExpressionWithDemorgansLawIntention : SelfTargetingOffsetIndependentIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java,
    KotlinBundle.messagePointer("demorgan.law")
) {
    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val expr = element.topmostBinaryExpression()
        setTextGetter(
            when (expr.operationToken) {
                KtTokens.ANDAND -> KotlinBundle.messagePointer("replace.&&.with.||")
                KtTokens.OROR -> KotlinBundle.messagePointer("replace.||.with.&&")
                else -> return false
            }
        )
        return Holder.splitBooleanSequence(expr) != null
    }

    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        Holder.applyTo(element)
    }

    object Holder {
        fun convertIfPossible(element: KtBinaryExpression) {
            val expr = element.topmostBinaryExpression()
            if (splitBooleanSequence(expr) == null) return
            applyTo(element)
        }

        internal fun KtBinaryExpression.topmostBinaryExpression(): KtBinaryExpression =
            parentsWithSelf.takeWhile { it is KtBinaryExpression }.last() as KtBinaryExpression

        internal fun applyTo(element: KtBinaryExpression) {
            val expr = element.topmostBinaryExpression()
            val operatorText = when (expr.operationToken) {
                KtTokens.ANDAND -> KtTokens.OROR.value
                KtTokens.OROR -> KtTokens.ANDAND.value
                else -> throw IllegalArgumentException()
            }
            val context by lazy { expr.analyze(BodyResolveMode.PARTIAL) }
            val operands = splitBooleanSequence(expr) { context }?.asReversed() ?: return
            val newExpression = KtPsiFactory(expr.project).buildExpression {
                val negatedOperands = operands.map {
                    it.safeAs<KtQualifiedExpression>()?.invertSelectorFunction(context) ?: it.negate()
                }
                appendExpressions(negatedOperands, separator = operatorText)
            }
            expr.parents.match(KtParenthesizedExpression::class, last = KtPrefixExpression::class)
                ?.takeIf { it.operationReference.getReferencedNameElementType() == KtTokens.EXCL }
                ?.replace(newExpression)
                ?: expr.replace(newExpression.negate())
        }

        internal fun splitBooleanSequence(expression: KtBinaryExpression, contextProvider: (() -> BindingContext)? = null): List<KtExpression>? {
            val result = ArrayList<KtExpression>()
            val firstOperator = expression.operationToken
            var remainingExpression: KtExpression = expression
            while (true) {
                if (remainingExpression !is KtBinaryExpression) break

                if (deparenthesize(remainingExpression.left) is KtStatementExpression ||
                    deparenthesize(remainingExpression.right) is KtStatementExpression
                ) return null

                val operation = remainingExpression.operationToken
                if (operation != KtTokens.ANDAND && operation != KtTokens.OROR) break
                if (operation != firstOperator) return null //Boolean sequence must be homogenous

                result.add(remainingExpression.right ?: return null)
                remainingExpression = remainingExpression.left ?: return null
            }
            result.add(remainingExpression)

            val context = contextProvider?.invoke() ?: expression.analyze(BodyResolveMode.PARTIAL)
            if (!expression.left.isBoolean(context) || !expression.right.isBoolean(context)) return null
            return result
        }

        private fun KtExpression?.isBoolean(context: BindingContext) = this != null && context.getType(this)?.isBoolean() == true
    }
}
