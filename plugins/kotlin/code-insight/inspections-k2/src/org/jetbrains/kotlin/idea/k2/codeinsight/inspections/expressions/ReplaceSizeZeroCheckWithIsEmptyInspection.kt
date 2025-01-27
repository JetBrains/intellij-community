// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.expressions

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.isOneIntegerConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isZeroIntegerConstant
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

internal class ReplaceSizeZeroCheckWithIsEmptyInspection : ReplaceSizeCheckInspectionBase() {

    override val methodToReplaceWith: EmptinessCheckMethod
        get() = EmptinessCheckMethod.IS_EMPTY

    override fun createQuickFixes(
        element: KtBinaryExpression,
        context: ReplacementInfo,
    ): Array<KotlinModCommandQuickFix<KtBinaryExpression>> = arrayOf(object : ReplaceSizeCheckQuickFixBase(context) {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.size.zero.check.with.isempty")
    })

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: ReplacementInfo,
    ): String = KotlinBundle.message("inspection.replace.size.zero.check.with.is.empty.display.name")

    override fun extractTargetExpressionFromPsi(expr: KtBinaryExpression): KtExpression? {
        val left = expr.left ?: return null
        val right = expr.right ?: return null

        return when (expr.operationToken) {
            KtTokens.EQEQ -> when {
                right.isZeroIntegerConstant -> left
                left.isZeroIntegerConstant -> right
                else -> null
            }

            KtTokens.GTEQ -> if (left.isZeroIntegerConstant) right else null
            KtTokens.GT -> if (left.isOneIntegerConstant) right else null
            KtTokens.LTEQ -> if (right.isZeroIntegerConstant) left else null
            KtTokens.LT -> if (right.isOneIntegerConstant) left else null
            else -> null
        }
    }
}