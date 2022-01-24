// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isChar
import org.jetbrains.kotlin.types.typeUtil.isSignedOrUnsignedNumberType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

object EqualityNotApplicableFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val diagnosticWithParameters = Errors.EQUALITY_NOT_APPLICABLE.cast(diagnostic)
        val binary = diagnostic.psiElement as? KtBinaryExpression ?: return emptyList()
        val left = binary.left ?: return emptyList()
        val right = binary.right ?: return emptyList()
        val leftType = diagnosticWithParameters.b
        val rightType = diagnosticWithParameters.c

        val fixes = mutableListOf<IntentionAction>()

        if (leftType.isNumberType() && rightType.isNumberType()) {
            fixes.add(
                NumberConversionFix(left, leftType, rightType, enableNullableType = true) {
                    KotlinBundle.message("convert.left.hand.side.to.0", it)
                }
            )
            fixes.add(
                NumberConversionFix(right, rightType, leftType, enableNullableType = true) {
                    KotlinBundle.message("convert.right.hand.side.to.0", it)
                }
            )
        }

        val leftHandSideIsChar = leftType.isChar()
        val rightHandSideIsChar = rightType.isChar()
        if ((leftHandSideIsChar && right is KtStringTemplateExpression) ||
            (rightHandSideIsChar && left is KtStringTemplateExpression)
        ) {
            val stringTemplate = (if (leftHandSideIsChar) right else left) as KtStringTemplateExpression
            if (ConvertStringToCharLiteralFix.isApplicable(stringTemplate)) {
                fixes.add(ConvertStringToCharLiteralFix(stringTemplate))
            }
        }

        return fixes
    }

    private fun KotlinType.isNumberType() = this.makeNotNullable().isSignedOrUnsignedNumberType()
}
