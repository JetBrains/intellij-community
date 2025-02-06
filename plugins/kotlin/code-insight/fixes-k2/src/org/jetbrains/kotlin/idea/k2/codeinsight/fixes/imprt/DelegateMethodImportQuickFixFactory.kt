// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPropertyDelegatePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPropertyDelegate

internal class DelegateMethodImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, KotlinRawPositionContext>? {
        return when (diagnostic) {
            is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable,
            is KaFirDiagnostic.DelegateSpecialFunctionMissing -> {
                val delegateExpression = diagnostic.psi
                val propertyDelegate = delegateExpression.parent as? KtPropertyDelegate ?: return null

                delegateExpression to KotlinPropertyDelegatePositionContext(propertyDelegate)
            }

            else -> null
        }
    }

    override fun KaSession.provideImportCandidates(
      diagnostic: KaDiagnosticWithPsi<*>,
      positionContext: KotlinRawPositionContext,
      indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        if (positionContext !is KotlinPropertyDelegatePositionContext) return emptyList()
        val providers = getCandidateProvidersForDelegatedProperty(diagnostic, positionContext)
        return providers.flatMap { it.collectCandidates(indexProvider) }.toList()
    }

    private fun getCandidateProvidersForDelegatedProperty(
      diagnostic: KaDiagnosticWithPsi<*>,
      positionContext: KotlinPropertyDelegatePositionContext,
    ): Sequence<AbstractImportCandidatesProvider> {
        val expectedFunctionSignature = when (diagnostic) {
            is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable -> diagnostic.expectedFunctionSignature
            is KaFirDiagnostic.DelegateSpecialFunctionMissing -> diagnostic.expectedFunctionSignature
            else -> null
        }

        if (expectedFunctionSignature == null) return emptySequence()

        return sequenceOf(DelegateMethodImportCandidatesProvider(expectedFunctionSignature, positionContext))
    }
}