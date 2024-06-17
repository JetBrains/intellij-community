// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeNullability
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.NumberConversionFix
import org.jetbrains.kotlin.types.Variance

internal object NumberConversionFixFactory {

    val numberConversionFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.EqualityNotApplicable ->
        val binaryExpr = diagnostic.psi
        val left = binaryExpr.left ?: return@ModCommandBased emptyList()
        val right = binaryExpr.right ?: return@ModCommandBased emptyList()
        val leftType = diagnostic.leftType
        val rightType = diagnostic.rightType


        if (!isNumberConversionAvailable(leftType, rightType)) return@ModCommandBased emptyList()

        listOf(
            NumberConversionFix(
                element = left,
                elementContext = prepareNumberConversionElementContext(leftType, rightType),
                conversionType = NumberConversionFix.ConversionType.LEFT_HAND_SIDE
            ),

            NumberConversionFix(
                element = right,
                elementContext = prepareNumberConversionElementContext(rightType, leftType),
                conversionType = NumberConversionFix.ConversionType.RIGHT_HAND_SIDE
            ),
        )
    }
}

context(KaSession)
internal fun prepareNumberConversionElementContext(
    fromType: KaType,
    toType: KaType,
): NumberConversionFix.ElementContext {
    return NumberConversionFix.ElementContext(
        typePresentation = toType.withNullability(KaTypeNullability.NON_NULLABLE).render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.INVARIANT,
        ),
        fromInt = fromType.isInt,
        fromChar = fromType.isChar,
        fromFloatOrDouble = fromType.isFloat || fromType.isDouble,
        fromNullable = fromType.nullability.isNullable,
        toChar = toType.isChar,
        toInt = toType.isInt,
        toByteOrShort = toType.isByte || toType.isShort,
    )
}

context(KaSession)
internal fun isNumberConversionAvailable(
    fromType: KaType,
    toType: KaType,
): Boolean = fromType != toType && fromType.isNumberOrCharType() && toType.isNumberOrCharType()

context(KaSession)
internal fun KaType.isNumberOrCharType(): Boolean {
    return with(withNullability(KaTypeNullability.NON_NULLABLE)) {
        isNumberOrUNumberType() || isChar
    }
}
