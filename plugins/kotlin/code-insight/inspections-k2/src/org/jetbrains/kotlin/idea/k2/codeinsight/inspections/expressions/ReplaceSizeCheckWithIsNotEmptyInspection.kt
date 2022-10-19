// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

internal class ReplaceSizeCheckWithIsNotEmptyInspection : ReplaceSizeCheckInspectionBase() {

    override val methodToReplaceWith = EmptinessCheckMethod.IS_NOT_EMPTY

    override fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression? =
        when (expr.operationToken) {
            KtTokens.EXCLEQ -> when {
                expr.right?.isZeroIntegerConstant() == true -> expr.left
                expr.left?.isZeroIntegerConstant() == true -> expr.right
                else -> null
            }

            KtTokens.GTEQ -> if (expr.right?.isOneIntegerConstant() == true) expr.left else null
            KtTokens.GT -> if (expr.right?.isZeroIntegerConstant() == true) expr.left else null
            KtTokens.LTEQ -> if (expr.left?.isOneIntegerConstant() == true) expr.right else null
            KtTokens.LT -> if (expr.left?.isZeroIntegerConstant() == true) expr.right else null
            else -> null
        }
}