// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RemoveTypeVarianceFix
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.types.Variance

internal object TypeVarianceConflictErrorFixFactory {

    @OptIn(KaExperimentalApi::class)
    val removeTypeVariance = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.TypeVarianceConflictError ->
        val typeReference = diagnostic.psi as? KtTypeReference ?: return@ModCommandBased emptyList()
        val type = typeReference.type as? KaTypeParameterType ?: return@ModCommandBased emptyList()
        val symbol = type.symbol
        val variance = symbol.variance
        if (variance == Variance.INVARIANT) return@ModCommandBased emptyList()
        val typeParameter = symbol.psi as? KtTypeParameter ?: return@ModCommandBased emptyList()

        listOf(
            RemoveTypeVarianceFix(
                typeParameter,
                variance,
                type.render(
                    renderer = KaTypeRendererForSource.WITH_QUALIFIED_NAMES,
                    position = Variance.INVARIANT,
                ),
            )
        )
    }
}