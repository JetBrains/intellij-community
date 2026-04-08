// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.ReplacePrimitiveCastWithNumberConversionFix
import org.jetbrains.kotlin.psi.KtBinaryExpressionWithTypeRHS
import org.jetbrains.kotlin.types.Variance

internal object ReplacePrimitiveCastWithNumberConversionFixFactory {
    val replaceIncompatibleNumberCastWithNumberConversionFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NumericCastNeverSucceedsButCanBeReplacedWithToCall ->
        getFixes(diagnostic.psi, diagnostic.targetType)
    }

    val replaceRedundantNumberCastWithNumberConversionFixFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.IntegerLiteralCastInsteadOfToCall ->
        getFixes(diagnostic.psi, diagnostic.targetType)
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.getFixes(element: KtBinaryExpressionWithTypeRHS, targetType: KaType): List<ReplacePrimitiveCastWithNumberConversionFix> {
        return listOf(
            ReplacePrimitiveCastWithNumberConversionFix(
                element,
                targetType.render(renderer = KaTypeRendererForSource.WITH_SHORT_NAMES, position = Variance.INVARIANT)
            )
        )
    }
}
