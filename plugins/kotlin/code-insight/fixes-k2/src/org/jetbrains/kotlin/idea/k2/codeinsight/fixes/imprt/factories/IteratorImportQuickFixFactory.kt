// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.util.OperatorNameConventions

/**
 * Import quick fix factory for all iteration-related operator functions:
 * - `iterator()` - for iterating over collections in for-loops
 * - `hasNext()` - for checking if iterator has more elements
 * - `next()` - for getting the next element from iterator
 */
internal object IteratorImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        val iteratedExpression = diagnostic.psi as? KtExpression ?: return null
        
        return when (diagnostic) {
            is KaFirDiagnostic.IteratorMissing,
            is KaFirDiagnostic.IteratorAmbiguity -> {
                DefaultImportContext(iteratedExpression, ImportPositionTypeAndReceiver.OperatorCall(iteratedExpression))
            }
            
            is KaFirDiagnostic.HasNextMissing,
            is KaFirDiagnostic.HasNextFunctionNoneApplicable,
            is KaFirDiagnostic.NextMissing,
            is KaFirDiagnostic.NextNoneApplicable -> {
                val iteratorType = resolveIteratorType(iteratedExpression) ?: return null

                ImportContextWithFixedReceiverType(
                    iteratedExpression,
                    ImportPositionType.OperatorCall,
                    explicitReceiverType = iteratorType
                )
            }

            else -> null
        }
    }
    
    /**
     * Resolves the type of the iterator returned by calling `iterator()` on the given [iteratedExpression].
     */
    @OptIn(KaExperimentalApi::class)
    private fun KaSession.resolveIteratorType(iteratedExpression: KtExpression): KaType? {
        val psiFactory = KtPsiFactory.contextual(iteratedExpression)

        val iteratorCallExpression =
            psiFactory.createExpressionCodeFragment("${iteratedExpression.text}.iterator()", iteratedExpression).getContentElement()
                ?: return null

        return analyze(iteratorCallExpression) { iteratorCallExpression.expressionType?.createPointer() }?.restore()
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importPositionContext: ImportContext): Set<Name> = 
        when (diagnostic) {
            is KaFirDiagnostic.IteratorMissing,
            is KaFirDiagnostic.IteratorAmbiguity -> setOf(OperatorNameConventions.ITERATOR)
            
            is KaFirDiagnostic.HasNextMissing,
            is KaFirDiagnostic.HasNextFunctionNoneApplicable -> setOf(OperatorNameConventions.HAS_NEXT)
            
            is KaFirDiagnostic.NextMissing,
            is KaFirDiagnostic.NextNoneApplicable -> setOf(OperatorNameConventions.NEXT)
            
            else -> emptySet()
        }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importPositionContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider,
    ): List<ImportCandidate> {
        val provider = CallableImportCandidatesProvider(importPositionContext)
        return provider.collectCandidates(unresolvedName, indexProvider)
    }
}
