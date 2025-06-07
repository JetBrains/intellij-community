// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.name.Name

internal abstract class AbstractImportQuickFixFactory : KotlinQuickFixFactory.IntentionBased<KaDiagnosticWithPsi<*>> {

    /**
     * Returns the detected [ImportPositionTypeAndReceiver] for the given diagnostic.
     */
    protected abstract fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext?

    protected abstract fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importContext: ImportContext): Set<Name>

    protected abstract fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate>

    override fun KaSession.createQuickFixes(diagnostic: KaDiagnosticWithPsi<*>): List<ImportQuickFix> =
        createQuickFixes(setOf(diagnostic))

    fun KaSession.createQuickFixes(diagnostics: Set<KaDiagnosticWithPsi<*>>): List<ImportQuickFix> {
        return diagnostics
            .mapNotNull { diagnostic ->
                val positionContext = detectPositionContext(diagnostic) ?: return@mapNotNull null
                val unresolvedNames = provideUnresolvedNames(diagnostic, positionContext)

                val indexProvider = KtSymbolFromIndexProvider(positionContext.position.containingKtFile)

                val candidates = unresolvedNames.flatMap { provideImportCandidates(it, positionContext, indexProvider) }
                val data = ImportQuickFixProvider.createImportData(positionContext.position, candidates) ?: return@mapNotNull null
                ImportQuickFixProvider.run { createImportFix(positionContext.position, data) }
            }
    }
}