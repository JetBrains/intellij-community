// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.allOverriddenSymbols
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.symbol
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.RenameParameterToMatchOverriddenMethodFix
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter

internal object RenameParameterToMatchOverriddenMethodFixFactory {

    val renameParameterToMatchOverriddenMethod =
        KotlinQuickFixFactory.IntentionBased { diagnostic: KaFirDiagnostic.ParameterNameChangedOnOverride ->
            val parameter = diagnostic.psi

            val parameterFromSuperclassName = parameter.matchingParametersFromOverridenDeclarations()
                .map { it.nameAsSafeName }
                .distinct()
                .singleOrNull() ?: return@IntentionBased emptyList()

            listOf(RenameParameterToMatchOverriddenMethodFix(parameter, parameterFromSuperclassName))
        }

    context(_: KaSession)
    private fun KtParameter.matchingParametersFromOverridenDeclarations(): Sequence<KtParameter> {
        val index = ownerFunction?.valueParameters?.indexOf(this)
        if (index == null || index == -1) return emptySequence()

        val functionCallableSymbol = ownerFunction?.symbol as? KaCallableSymbol ?: return emptySequence()
        return functionCallableSymbol.allOverriddenSymbols.mapNotNull {
            val declaration = it.psi as? KtFunction ?: return@mapNotNull null
            declaration.valueParameters[index]
        }
    }
}