// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFix
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportQuickFixProvider
import org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement

internal abstract class AbstractImportQuickFixFactory : KotlinQuickFixFactory.IntentionBased<KaDiagnosticWithPsi<*>> {

    /**
     * Returns the [KtElement] to put an auto-import on, and the detected [org.jetbrains.kotlin.idea.util.positionContext.KotlinRawPositionContext] around it.
     */
    protected abstract fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, KotlinRawPositionContext>?
    
    protected abstract fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, positionContext: KotlinRawPositionContext): Set<Name>

    protected abstract fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        positionContext: KotlinRawPositionContext,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate>

    override fun KaSession.createQuickFixes(diagnostic: KaDiagnosticWithPsi<*>): List<ImportQuickFix> =
        createQuickFixes(setOf(diagnostic))

    fun KaSession.createQuickFixes(diagnostics: Set<KaDiagnosticWithPsi<*>>): List<ImportQuickFix> {
        return diagnostics
            .mapNotNull { diagnostic ->
                val (expression, positionContext) = detectPositionContext(diagnostic) ?: return@mapNotNull null
                val unresolvedNames = provideUnresolvedNames(diagnostic, positionContext)

                val indexProvider = KtSymbolFromIndexProvider(expression.containingKtFile)

                val candidates = unresolvedNames.flatMap { provideImportCandidates(it, positionContext, indexProvider) }
                val data = ImportQuickFixProvider.createImportData(expression, candidates) ?: return@mapNotNull null
                ImportQuickFixProvider.run { createImportFix(expression, data) }
            }
    }
}