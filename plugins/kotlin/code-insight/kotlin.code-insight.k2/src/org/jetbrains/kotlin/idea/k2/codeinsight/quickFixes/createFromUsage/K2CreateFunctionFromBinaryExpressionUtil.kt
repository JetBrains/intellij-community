// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage

import com.intellij.lang.jvm.actions.ExpectedParameter
import com.intellij.lang.jvm.actions.ExpectedType
import com.intellij.psi.PsiTypes
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.session.analyze
import org.jetbrains.kotlin.analysis.api.types.builtinTypes
import org.jetbrains.kotlin.analysis.api.expressions.expressionType
import org.jetbrains.kotlin.idea.k2.codeinsight.quickFixes.createFromUsage.K2CreateFunctionFromUsageUtil.getExpectedKotlinType
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.types.expressions.OperatorConventions

context(_: KaSession)
internal fun computeExpectedParamsForBinaryExpression(binaryExpression: KtBinaryExpression): List<ExpectedParameter> {
    val token = binaryExpression.operationToken
    val inOperation = token in OperatorConventions.IN_OPERATIONS

    val leftExpr = binaryExpression.left ?: return emptyList()
    val rightExpr = binaryExpression.right ?: return emptyList()

    val argumentExpr = if (inOperation) leftExpr else rightExpr
    val receiverExpr = if (inOperation) rightExpr else leftExpr

    return listOf(
        K2CreateFunctionFromUsageUtil.createExpectedParameterInfo(
            argumentExpression = argumentExpr,
            defaultParameterName = "p0",
            parameterNameAsString = null,
            isTheOnlyAnnotationParameter = false,
            receiverType = receiverExpr.expressionType,
        )
    )
}

context(_: KaSession)
internal fun createReturnTypeForBinaryExpression(binaryExpression: KtBinaryExpression): List<ExpectedType> {
    val token = binaryExpression.operationToken as KtToken
    val inOperation = token in OperatorConventions.IN_OPERATIONS
    val comparisonOperation = token in OperatorConventions.COMPARISON_OPERATIONS

    return when {
        inOperation -> listOf(ExpectedKotlinType.create(builtinTypes.boolean, PsiTypes.booleanType()))
        comparisonOperation -> listOf(ExpectedKotlinType.create(builtinTypes.int, PsiTypes.intType()))
        else -> binaryExpression.getExpectedKotlinType()?.let(::listOf) ?: emptyList()
    }
}