// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.types.impl.KaTypeRendererForSource
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix
import org.jetbrains.kotlin.idea.quickfix.MakeUpperBoundNotNullFix.Kind
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.types.Variance

internal object MakeUpperBoundNotNullFixFactories {

    val argumentTypeMismatch = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.ArgumentTypeMismatch ->
        createChangeUpperBoundActions(diagnostic.expectedType, diagnostic.actualType).orEmpty()
    }

    val nothingToOverride = KotlinQuickFixFactory.ModCommandBased { diagnostic: KaFirDiagnostic.NothingToOverride ->
        val declaration = diagnostic.declaration as? KaFunctionSymbol ?: return@ModCommandBased emptyList()
        diagnostic.candidates.filterIsInstance<KaFunctionSymbol>().flatMap {
            it.valueParameters.zip(declaration.valueParameters).flatMap { (a, e) ->
                createChangeUpperBoundActions(a.returnType, e.returnType).orEmpty()
            }
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun KaSession.createChangeUpperBoundActions(
        expectedType: KaType,
        actualType: KaType
    ): List<MakeUpperBoundNotNullFix>? {
        fun createUpperBoundNonNullableFix(
            actual: KaType,
            expected: KaType
        ): MakeUpperBoundNotNullFix? {
            val aParamType = actual as? KaTypeParameterType ?: return null
            if (!(aParamType.isNullable && !expected.isNullable)) return null

            val typeParameter = aParamType.symbol.psi as? KtTypeParameter ?: return null
            val existingBoundType = typeParameter.extendsBound?.type
            return if (existingBoundType != null) {
                if (existingBoundType.isMarkedNullable) {
                    MakeUpperBoundNotNullFix(
                        typeParameter,
                        Kind.ReplaceExistingUpperBound(
                            existingBoundType.withNullability(false)
                                .render(KaTypeRendererForSource.WITH_QUALIFIED_NAMES, Variance.IN_VARIANCE)
                        )
                    )
                } else {
                    null
                }
            } else {
                MakeUpperBoundNotNullFix(typeParameter, Kind.AddAnyAsUpperBound)
            }
        }

        createUpperBoundNonNullableFix(actualType, expectedType)?.let { return listOf(it) }

        val a = actualType as? KaClassType ?: return null
        val e = expectedType as? KaClassType ?: return null
        if (e.typeArguments.size != a.typeArguments.size) return null

        return a.typeArguments.zip(e.typeArguments).mapNotNull { (actual, expected) ->
            val aTypeArg = actual.type ?: return@mapNotNull null
            val eTypeArg = expected.type ?: return@mapNotNull null
            createUpperBoundNonNullableFix(aTypeArg, eTypeArg)
        }
    }
}
