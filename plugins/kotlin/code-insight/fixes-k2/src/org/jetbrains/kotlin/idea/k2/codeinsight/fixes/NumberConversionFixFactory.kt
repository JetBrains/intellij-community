// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isByteType
import org.jetbrains.kotlin.analysis.api.components.isCharType
import org.jetbrains.kotlin.analysis.api.components.isDoubleType
import org.jetbrains.kotlin.analysis.api.components.isFloatType
import org.jetbrains.kotlin.analysis.api.components.isIntType
import org.jetbrains.kotlin.analysis.api.components.isShortType
import org.jetbrains.kotlin.analysis.api.components.render
import org.jetbrains.kotlin.analysis.api.components.semanticallyEquals
import org.jetbrains.kotlin.analysis.api.components.withNullability
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
                actionNameProvider = NumberConversionFix.ActionNameProvider.LEFT_HAND_SIDE,
            ),

            NumberConversionFix(
                element = right,
                elementContext = prepareNumberConversionElementContext(rightType, leftType),
                actionNameProvider = NumberConversionFix.ActionNameProvider.RIGHT_HAND_SIDE,
            ),
        )
    }
}

context(_: KaSession)
@OptIn(KaExperimentalApi::class)
internal fun prepareNumberConversionElementContext(
    fromType: KaType,
    toType: KaType,
): NumberConversionFix.ElementContext {
    return NumberConversionFix.ElementContext(
        typePresentation = toType.withNullability(false).render(
            renderer = KaTypeRendererForSource.WITH_SHORT_NAMES,
            position = Variance.INVARIANT,
        ),
        fromInt = fromType.isIntType,
        fromChar = fromType.isCharType,
        fromFloatOrDouble = fromType.isFloatType || fromType.isDoubleType,
        fromNullable = fromType.nullability.isNullable,
        toChar = toType.isCharType,
        toInt = toType.isIntType,
        toByteOrShort = toType.isByteType || toType.isShortType,
    )
}

context(_: KaSession)
internal fun isNumberConversionAvailable(
    fromType: KaType,
    toType: KaType,
): Boolean = !fromType.semanticallyEquals(toType) && fromType.isNumberOrCharType() && toType.isNumberOrCharType()

context(session: KaSession)
internal fun KaType.isNumberOrCharType(): Boolean {
    return with(withNullability(false)) {
        with(session) { isNumberOrUNumberType(this@isNumberOrCharType) } || isCharType
    }
}
