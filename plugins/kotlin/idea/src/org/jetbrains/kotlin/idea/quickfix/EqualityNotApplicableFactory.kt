// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.idea.util.IdeDescriptorRenderers
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.types.typeUtil.isByte
import org.jetbrains.kotlin.types.typeUtil.isChar
import org.jetbrains.kotlin.types.typeUtil.isDouble
import org.jetbrains.kotlin.types.typeUtil.isFloat
import org.jetbrains.kotlin.types.typeUtil.isInt
import org.jetbrains.kotlin.types.typeUtil.isShort
import org.jetbrains.kotlin.types.typeUtil.isSignedOrUnsignedNumberType
import org.jetbrains.kotlin.types.typeUtil.makeNotNullable

@K1Deprecation
object EqualityNotApplicableFactory : KotlinIntentionActionsFactory() {
    override fun doCreateActions(diagnostic: Diagnostic): List<IntentionAction> {
        val diagnosticWithParameters = Errors.EQUALITY_NOT_APPLICABLE.cast(diagnostic)
        val binaryExpr = diagnostic.psiElement as? KtBinaryExpression ?: return emptyList()
        val left = binaryExpr.left ?: return emptyList()
        val right = binaryExpr.right ?: return emptyList()
        val leftType = diagnosticWithParameters.b
        val rightType = diagnosticWithParameters.c

        if (isNumberConversionAvailable(leftType, rightType, enableNullableType = true)) {
            return listOf(
                NumberConversionFix(
                    element = left,
                    elementContext = prepareNumberConversionElementContext(leftType, rightType),
                    actionNameProvider = NumberConversionFix.ActionNameProvider.LEFT_HAND_SIDE,
                ).asIntention(),

                NumberConversionFix(
                    element = right,
                    elementContext = prepareNumberConversionElementContext(rightType, leftType),
                    actionNameProvider = NumberConversionFix.ActionNameProvider.RIGHT_HAND_SIDE,
                ).asIntention(),
            )
        }

        val leftHandSideIsChar = leftType.isChar()
        val rightHandSideIsChar = rightType.isChar()
        if ((leftHandSideIsChar && right is KtStringTemplateExpression) ||
            (rightHandSideIsChar && left is KtStringTemplateExpression)
        ) {
            val stringTemplate = (if (leftHandSideIsChar) right else left) as KtStringTemplateExpression
            if (ConvertStringToCharLiteralFix.isApplicable(stringTemplate)) {
                return listOf(ConvertStringToCharLiteralFix(stringTemplate))
            }
        }

        return emptyList()
    }
}

@K1Deprecation
fun prepareNumberConversionElementContext(
    fromType: KotlinType,
    toType: KotlinType
): NumberConversionFix.ElementContext = NumberConversionFix.ElementContext(
    typePresentation = IdeDescriptorRenderers.SOURCE_CODE_SHORT_NAMES_NO_ANNOTATIONS.renderType(toType.makeNotNullable()),
    fromInt = fromType.isInt(),
    fromChar = fromType.isChar(),
    fromFloatOrDouble = fromType.isFloat() || fromType.isDouble(),
    fromNullable = fromType.isNullable(),
    toChar = toType.isChar(),
    toInt = toType.isInt(),
    toByteOrShort = toType.isByte() || toType.isShort(),
)

@K1Deprecation
fun isNumberConversionAvailable(
    fromType: KotlinType,
    toType: KotlinType,
    enableNullableType: Boolean = false,
): Boolean {
    return fromType != toType && fromType.isNumberType(enableNullableType) && toType.isNumberType(enableNullableType)
}

private fun KotlinType.isNumberType(enableNullableType: Boolean): Boolean {
    val type = if (enableNullableType) this.makeNotNullable() else this
    return type.isSignedOrUnsignedNumberType()
}
