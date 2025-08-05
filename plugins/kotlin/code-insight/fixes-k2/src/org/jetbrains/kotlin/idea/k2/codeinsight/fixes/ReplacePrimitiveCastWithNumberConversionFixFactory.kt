// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.isBooleanType
import org.jetbrains.kotlin.analysis.api.components.isPrimitive
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix
import org.jetbrains.kotlin.types.Variance

internal object ReplacePrimitiveCastWithNumberConversionFixFactory {

    @OptIn(KaExperimentalApi::class)
    val replacePrimitiveCastWithNumberConversionFixFactory =
        KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.CastNeverSucceeds ->
            val binaryExpression = diagnostic.psi

            val expressionType = binaryExpression.left.expressionType ?: return@ModCommandBased emptyList()
            if (!expressionType.isPrimitiveNumberType()) return@ModCommandBased emptyList()

            val castType = binaryExpression.right?.type ?: return@ModCommandBased emptyList()
            if (!castType.isPrimitiveNumberType()) return@ModCommandBased emptyList()

            listOf(
                ReplacePrimitiveCastWithNumberConversionFix(
                    binaryExpression,
                    castType.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
                )
            )
        }
}

context(_: KaSession)
fun KaType.isPrimitiveNumberType(): Boolean = isPrimitive && !isBooleanType
