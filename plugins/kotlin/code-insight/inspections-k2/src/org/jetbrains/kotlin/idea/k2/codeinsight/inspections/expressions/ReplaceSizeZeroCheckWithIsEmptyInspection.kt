// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.utils.isOneIntegerConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isZeroIntegerConstant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

internal class ReplaceSizeZeroCheckWithIsEmptyInspection : ReplaceSizeCheckInspectionBase() {

    override val methodToReplaceWith = EmptinessCheckMethod.IS_EMPTY

    override fun getActionFamilyName() = KotlinBundle.message("replace.size.zero.check.with.isempty")

    override fun getProblemDescription(element: KtBinaryExpression, context: ReplacementInfo) =
        KotlinBundle.message("inspection.replace.size.zero.check.with.is.empty.display.name")

    override fun getActionName(element: KtBinaryExpression, context: ReplacementInfo) =
        KotlinBundle.message("replace.size.check.with.0", context.fixMessage())

    override fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression? =
        when (expr.operationToken) {
            KtTokens.EQEQ -> when {
                expr.right?.isZeroIntegerConstant() == true -> expr.left
                expr.left?.isZeroIntegerConstant() == true -> expr.right
                else -> null
            }

            KtTokens.GTEQ -> if (expr.left?.isZeroIntegerConstant() == true) expr.right else null
            KtTokens.GT -> if (expr.left?.isOneIntegerConstant() == true) expr.right else null
            KtTokens.LTEQ -> if (expr.right?.isZeroIntegerConstant() == true) expr.left else null
            KtTokens.LT -> if (expr.right?.isOneIntegerConstant() == true) expr.left else null
            else -> null
        }
}