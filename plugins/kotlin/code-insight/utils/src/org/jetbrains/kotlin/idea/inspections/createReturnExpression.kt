// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.psi.*

fun createReturnExpressionText(
    expr: KtExpression,
    labelName: String?
): String {
    val label = labelName?.let { "@$it" }.orEmpty()
    return when (expr) {
        is KtBreakExpression, is KtContinueExpression, is KtReturnExpression, is KtThrowExpression -> ""
        else -> {
            analyze(expr) {
                if (expr.expressionType?.isNothingType == true) {
                    ""
                } else {
                    "return$label "
                }
            }
        }
    }
}

fun createReturnExpression(
    expr: KtExpression,
    labelName: String?,
    psiFactory: KtPsiFactory
): KtExpression {
    val returnText = createReturnExpressionText(expr, labelName)
    return psiFactory.createExpressionByPattern("$returnText$0", expr)
}