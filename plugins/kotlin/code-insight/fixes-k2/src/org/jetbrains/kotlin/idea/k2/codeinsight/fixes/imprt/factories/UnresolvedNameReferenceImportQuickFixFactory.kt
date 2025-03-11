// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.highlighter.operationReferenceForBinaryExpressionOrThis
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtSimpleNameExpression

internal object UnresolvedNameReferenceImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportPositionContext<*, *>? {
        return when (diagnostic) {
            is KaFirDiagnostic.UnresolvedImport,
            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver,
            is KaFirDiagnostic.InvisibleReference -> {
                val diagnosticPsi = diagnostic.psi.operationReferenceForBinaryExpressionOrThis as? KtElement ?: return null
                ImportPositionContext.detect(diagnosticPsi)
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importPositionContext: ImportPositionContext<*, *>): Set<Name> {
        return (importPositionContext.position as? KtSimpleNameExpression)?.mainReference?.resolvesByNames?.toSet().orEmpty()
    }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importPositionContext: ImportPositionContext<*, *>,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val providers = getCandidateProvidersForUnresolvedNameReference(importPositionContext)
        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        importPositionContext: ImportPositionContext<*, *>,
    ): Sequence<AbstractImportCandidatesProvider> = when (importPositionContext) {
        is ImportPositionContext.TypeReference -> sequenceOf(
            ClassifierImportCandidatesProvider(importPositionContext),
        )

        is ImportPositionContext.Annotation -> sequenceOf(
            AnnotationImportCandidatesProvider(importPositionContext),
        )

        is ImportPositionContext.DefaultCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionContext),
            ClassifierImportCandidatesProvider(importPositionContext),
            EnumEntryImportCandidatesProvider(importPositionContext),
        )

        is ImportPositionContext.DotCall,
        is ImportPositionContext.SafeCall,
        is ImportPositionContext.InfixCall,
        is ImportPositionContext.OperatorCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionContext),
        )

        is ImportPositionContext.KDocNameReference -> sequenceOf(
            // TODO this is currently reported by KDocUnresolvedReferenceInspection
        )

        is ImportPositionContext.CallableReference -> sequenceOf(
            CallableImportCandidatesProvider(importPositionContext),
            ConstructorReferenceImportCandidatesProvider(importPositionContext),
        )

        else -> sequenceOf()
    }
}