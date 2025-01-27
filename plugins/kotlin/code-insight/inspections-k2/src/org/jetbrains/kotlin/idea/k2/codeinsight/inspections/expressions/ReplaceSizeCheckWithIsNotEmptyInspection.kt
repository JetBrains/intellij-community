// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isOneIntegerConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isZeroIntegerConstant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

internal class ReplaceSizeCheckWithIsNotEmptyInspection : ReplaceSizeCheckInspectionBase() {

    override val methodToReplaceWith: EmptinessCheckMethod
        get() = EmptinessCheckMethod.IS_NOT_EMPTY

    override fun createQuickFixes(
        element: KtBinaryExpression,
        context: ReplacementInfo,
    ): Array<KotlinModCommandQuickFix<KtBinaryExpression>> = arrayOf(object : ReplaceSizeCheckQuickFixBase(context) {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.size.check.with.isnotempty")
    })

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: ReplacementInfo,
    ): String = KotlinBundle.message("inspection.replace.size.check.with.is.not.empty.display.name")

    override fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression? {
        val left = expr.left ?: return null
        val right = expr.right ?: return null

        return when (expr.operationToken) {
            KtTokens.EXCLEQ -> when {
                right.isZeroIntegerConstant -> left
                left.isZeroIntegerConstant -> right
                else -> null
            }

            KtTokens.GTEQ -> if (right.isOneIntegerConstant) left else null
            KtTokens.GT -> if (right.isZeroIntegerConstant) left else null
            KtTokens.LTEQ -> if (left.isOneIntegerConstant) right else null
            KtTokens.LT -> if (left.isZeroIntegerConstant) right else null
            else -> null
        }
    }
}