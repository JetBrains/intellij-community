// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.AbstractImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.DelegateMethodImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.util.positionContext.KotlinPropertyDelegatePositionContext
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtPropertyDelegate
import org.jetbrains.kotlin.util.OperatorNameConventions

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

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, positionContext: KotlinRawPositionContext): Set<Name> {
        val expectedFunctionSignature = when (diagnostic) {
            is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable -> diagnostic.expectedFunctionSignature
            is KaFirDiagnostic.DelegateSpecialFunctionMissing -> diagnostic.expectedFunctionSignature
            else -> null
        }
        
        if (expectedFunctionSignature == null) return emptySet()

        val expectedDelegateFunctionName: Name? = listOf(
            OperatorNameConventions.GET_VALUE,
            OperatorNameConventions.SET_VALUE,
        ).singleOrNull { expectedFunctionSignature.startsWith(it.asString() + "(") }

        return setOfNotNull(
            expectedDelegateFunctionName,
            OperatorNameConventions.PROVIDE_DELEGATE,
        )
    }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        positionContext: KotlinRawPositionContext,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        if (positionContext !is KotlinPropertyDelegatePositionContext) return emptyList()
        val providers = getCandidateProvidersForDelegatedProperty(positionContext)
        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    private fun getCandidateProvidersForDelegatedProperty(
        positionContext: KotlinPropertyDelegatePositionContext,
    ): Sequence<AbstractImportCandidatesProvider> {
        return sequenceOf(DelegateMethodImportCandidatesProvider(positionContext))
    }
}