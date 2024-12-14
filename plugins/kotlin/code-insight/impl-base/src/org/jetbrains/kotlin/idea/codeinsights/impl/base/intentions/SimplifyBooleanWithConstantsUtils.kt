// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions

import com.intellij.psi.tree.IElementType
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.base.KaConstantValue
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf

@ApiStatus.Internal
object SimplifyBooleanWithConstantsUtils {
    fun KtBinaryExpression.topBinary(): KtBinaryExpression =
        this.parentsWithSelf.takeWhile { it is KtBinaryExpression }.lastOrNull() as? KtBinaryExpression ?: this

    fun performSimplification(element: KtBinaryExpression) {
        val topBinary = element.topBinary()
        val simplified = toSimplifiedExpression(topBinary)
        val result = topBinary.replaced(KtPsiUtil.safeDeparenthesize(simplified, true))
        removeRedundantAssertion(result)
    }

    private fun toSimplifiedExpression(expression: KtExpression): KtExpression {
        val psiFactory = KtPsiFactory(expression.project)

        when {
            expression.canBeReducedToTrue() -> {
                return psiFactory.createExpression("true")
            }

            expression.canBeReducedToFalse() -> {
                return psiFactory.createExpression("false")
            }

            expression is KtParenthesizedExpression -> {
                val expr = expression.expression
                if (expr != null) {
                    val simplified = toSimplifiedExpression(expr)
                    return if (simplified is KtBinaryExpression) {
                        // wrap in new parentheses to keep the user's original format
                        psiFactory.createExpressionByPattern("($0)", simplified)
                    } else {
                        // if we now have a simpleName, constant, or parenthesized we don't need parentheses
                        simplified
                    }
                }
            }

            expression is KtBinaryExpression -> {
                if (!areThereExpressionsToBeSimplified(expression)) return expression.copied()
                val left = expression.left
                val right = expression.right
                val op = expression.operationToken
                if (left != null && right != null && (op == ANDAND || op == OROR || op == EQEQ || op == EXCLEQ)) {
                    val simpleLeft = simplifyExpression(left)
                    val simpleRight = simplifyExpression(right)
                    return when {
                        simpleLeft.canBeReducedToTrue() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(true, simpleRight, op)

                        simpleLeft.canBeReducedToFalse() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(false, simpleRight, op)

                        simpleRight.canBeReducedToTrue() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(true, simpleLeft, op)

                        simpleRight.canBeReducedToFalse() -> toSimplifiedBooleanBinaryExpressionWithConstantOperand(false, simpleLeft, op)

                        else -> {
                            val opText = expression.operationReference.text
                            psiFactory.createExpressionByPattern("$0 $opText $1", simpleLeft, simpleRight)
                        }
                    }
                }
            }
        }

        return expression.copied()
    }

    private fun simplifyExpression(expression: KtExpression): KtExpression = expression.replaced(toSimplifiedExpression(expression))

    private fun toSimplifiedBooleanBinaryExpressionWithConstantOperand(
        constantOperand: Boolean,
        otherOperand: KtExpression,
        operation: IElementType
    ): KtExpression {
        val psiFactory = KtPsiFactory(otherOperand.project)
        when (operation) {
            OROR -> {
                if (constantOperand) return psiFactory.createExpression("true")
            }
            ANDAND -> {
                if (!constantOperand) return psiFactory.createExpression("false")
            }
            EQEQ, EXCLEQ -> toSimplifiedExpression(otherOperand).let {
                return if (constantOperand == (operation == EQEQ)) it
                else psiFactory.createExpressionByPattern("!$0", it)
            }
        }

        return toSimplifiedExpression(otherOperand)
    }

    private fun KtExpression.canBeReducedToTrue(): Boolean = canBeReducedToBooleanConstant(this, true)

    private fun KtExpression.canBeReducedToFalse(): Boolean = canBeReducedToBooleanConstant(this, false)

    private fun canBeReducedToBooleanConstant(expression: KtExpression, expectedValue: Boolean? = null): Boolean {
        analyze(expression) {
            val value = (expression.evaluate() as? KaConstantValue.BooleanValue)?.value ?: return false
            
            return expectedValue == null || value == expectedValue
        }
    }

    fun removeRedundantAssertion(expression: KtExpression) {
        val callExpression = expression.getNonStrictParentOfType<KtCallExpression>() ?: return
        val fqName = analyze(callExpression) {
            val resolvedCall = callExpression.resolveToCall()?.successfulFunctionCallOrNull() ?: return
            resolvedCall.symbol.callableId?.let {
                it.packageName.asString() + "." + it.callableName.asString()
            }
        }
        val valueArguments = callExpression.valueArguments
        val isRedundant = fqName == "kotlin.assert"
                && valueArguments.singleOrNull()?.getArgumentExpression().isTrueConstant()
        if (isRedundant) callExpression.delete()
    }

    fun areThereExpressionsToBeSimplified(element: KtExpression?): Boolean {
        if (element == null) return false
        when (element) {
            is KtParenthesizedExpression -> return areThereExpressionsToBeSimplified(element.expression)

            is KtBinaryExpression -> {
                val op = element.operationToken
                if (op == ANDAND || op == OROR || op == EQEQ || op == EXCLEQ) {
                    if (
                        areThereExpressionsToBeSimplified(element.left) && element.right?.let(::hasNotNullableBooleanType) == true ||
                        areThereExpressionsToBeSimplified(element.right) && element.left?.let(::hasNotNullableBooleanType) == true
                    ) return true
                }
                if (isPositiveNegativeZeroComparison(element)) return false
            }
        }

        return canBeReducedToBooleanConstant(element)
    }
    
    private fun isPositiveNegativeZeroComparison(element: KtBinaryExpression): Boolean {
        val op = element.operationToken
        if (op != EQEQ && op != EQEQEQ) {
            return false
        }

        val left = element.left?.safeDeparenthesize() ?: return false
        val right = element.right?.safeDeparenthesize() ?: return false

        fun KtExpression.getConstantValue(): Any? =
            analyze(this) { evaluate()?.value }

        val leftValue = left.getConstantValue()
        val rightValue = right.getConstantValue()

        fun isPositiveZero(value: Any?) = value == +0.0 || value == +0.0f
        fun isNegativeZero(value: Any?) = value == -0.0 || value == -0.0f

        val hasPositiveZero = isPositiveZero(leftValue) || isPositiveZero(rightValue)
        val hasNegativeZero = isNegativeZero(leftValue) || isNegativeZero(rightValue)

        return hasPositiveZero && hasNegativeZero
    }

    private fun hasNotNullableBooleanType(expression: KtExpression): Boolean {
        analyze(expression) {
            val ktType = expression.expressionType ?: return false
            return ktType.isBooleanType && !ktType.canBeNull
        }
    }
}