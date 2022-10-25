// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsight.utils

import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KtAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.lifetime.allowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.types.KtType
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

object NegatedBinaryExpressionSimplificationUtils {
    fun simplifyNegatedBinaryExpressionIfNeeded(expression: KtPrefixExpression) {
        if (expression.canBeSimplifiedWithoutChangingSemantics()) expression.simplify()
    }

    fun KtPrefixExpression.canBeSimplifiedWithoutChangingSemantics(): Boolean {
        if (!canBeSimplified()) return false
        val expression = KtPsiUtil.deparenthesize(baseExpression) as? KtBinaryExpression ?: return true
        val operation = expression.operationReference.getReferencedNameElementType()
        if (operation != KtTokens.LT && operation != KtTokens.LTEQ && operation != KtTokens.GT && operation != KtTokens.GTEQ) return true

        @OptIn(KtAllowAnalysisOnEdt::class)
        allowAnalysisOnEdt {
            analyze(expression) {
                fun KtType?.isFloatingPoint() = this != null && (isFloat || isDouble)
                return !expression.left?.getKtType().isFloatingPoint() && !expression.right?.getKtType().isFloatingPoint()
            }
        }
    }

    fun KtPrefixExpression.canBeSimplified(): Boolean {
        if (operationToken != KtTokens.EXCL) return false

        val expression = KtPsiUtil.deparenthesize(baseExpression) as? KtOperationExpression ?: return false
        when (expression) {
            is KtIsExpression -> if (expression.typeReference == null) return false
            is KtBinaryExpression -> if (expression.left == null || expression.right == null) return false
            else -> return false
        }

        return (expression.operationReference.getReferencedNameElementType() as? KtSingleValueToken)?.negate() != null
    }

    fun KtPrefixExpression.simplify() {
        val expression = KtPsiUtil.deparenthesize(baseExpression) ?: return
        val operation =
            (expression as KtOperationExpression).operationReference.getReferencedNameElementType().negate()?.value ?: return

        val psiFactory = KtPsiFactory(expression)
        val newExpression = when (expression) {
            is KtIsExpression ->
                psiFactory.createExpressionByPattern("$0 $1 $2", expression.leftHandSide, operation, expression.typeReference!!)
            is KtBinaryExpression ->
                psiFactory.createExpressionByPattern("$0 $1 $2", expression.left ?: return, operation, expression.right ?: return)
            else ->
                throw IllegalArgumentException()
        }
        replace(newExpression)
    }

    fun IElementType.negate(): KtSingleValueToken? = when (this) {
        KtTokens.IN_KEYWORD -> KtTokens.NOT_IN
        KtTokens.NOT_IN -> KtTokens.IN_KEYWORD

        KtTokens.IS_KEYWORD -> KtTokens.NOT_IS
        KtTokens.NOT_IS -> KtTokens.IS_KEYWORD

        KtTokens.EQEQ -> KtTokens.EXCLEQ
        KtTokens.EXCLEQ -> KtTokens.EQEQ
        KtTokens.EQEQEQ -> KtTokens.EXCLEQEQEQ
        KtTokens.EXCLEQEQEQ -> KtTokens.EQEQEQ

        KtTokens.LT -> KtTokens.GTEQ
        KtTokens.GTEQ -> KtTokens.LT

        KtTokens.GT -> KtTokens.LTEQ
        KtTokens.LTEQ -> KtTokens.GT

        else -> null
    }
}