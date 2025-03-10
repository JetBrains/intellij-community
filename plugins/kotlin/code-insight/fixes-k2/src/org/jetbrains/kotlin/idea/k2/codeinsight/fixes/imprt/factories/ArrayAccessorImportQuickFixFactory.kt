// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.CallableImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.util.OperatorNameConventions

internal object ArrayAccessorImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, ImportPositionContext<*, *>>? {
        return when (diagnostic) {
            is KaFirDiagnostic.NoGetMethod,
            is KaFirDiagnostic.NoSetMethod -> {
                val arrayAccess = diagnostic.psi
                val arrayExpression = arrayAccess.arrayExpression ?: return null

                val importPositionContext = ImportPositionContext.OperatorCall(arrayAccess, arrayExpression)

                importPositionContext.position to importPositionContext
            }
            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importPositionContext: ImportPositionContext<*, *>): Set<Name> {
        val unresolvedName = when (diagnostic) {
            is KaFirDiagnostic.NoGetMethod -> OperatorNameConventions.GET
            is KaFirDiagnostic.NoSetMethod -> OperatorNameConventions.SET
            else -> null
        }

        return setOfNotNull(unresolvedName)
    }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importPositionContext: ImportPositionContext<*, *>,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val provider = CallableImportCandidatesProvider(importPositionContext)
        return provider.collectCandidates(unresolvedName, indexProvider)
    }
}
