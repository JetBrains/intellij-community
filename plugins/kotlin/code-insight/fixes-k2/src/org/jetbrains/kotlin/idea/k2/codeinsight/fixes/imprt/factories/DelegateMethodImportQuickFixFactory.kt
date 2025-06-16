// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object DelegateMethodImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        return when (diagnostic) {
            is KaFirDiagnostic.DelegateSpecialFunctionNoneApplicable,
            is KaFirDiagnostic.DelegateSpecialFunctionMissing -> {
                val delegateExpression = diagnostic.psi
                DefaultImportContext(delegateExpression, ImportPositionTypeAndReceiver.OperatorCall(delegateExpression))
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importContext: ImportContext): Set<Name> {
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
        importContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val providers = CallableImportCandidatesProvider(importContext)
        return providers.collectCandidates(unresolvedName, indexProvider)
    }
}