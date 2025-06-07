// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.*

/**
 * Creates "return" with an optional label or an empty string depending on the [expression] type.
 */
fun createReturnOrEmptyText(
    expression: KtExpression,
    labelName: String?
): String {
    val label = labelName?.let { "@$it" }.orEmpty()
    return when (expression) {
        is KtBreakExpression, is KtContinueExpression, is KtReturnExpression, is KtThrowExpression -> ""
        else -> {
            analyze(expression) {
                if (expression.expressionType?.isNothingType == true) {
                    ""
                } else {
                    "return$label "
                }
            }
        }
    }
}

/**
 * Creates a return expression that may vary depending on the [expression] type.
 */
fun createReturnExpression(
    expression: KtExpression,
    labelName: String?,
    psiFactory: KtPsiFactory
): KtExpression {
    val returnText = createReturnOrEmptyText(expression, labelName)
    return psiFactory.createExpressionByPattern("$returnText$0", expression)
}