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
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportPositionTypeAndReceiver<*, *>? {
        return when (diagnostic) {
            is KaFirDiagnostic.UnresolvedImport,
            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver,
            is KaFirDiagnostic.InvisibleReference -> {
                val diagnosticPsi = diagnostic.psi.operationReferenceForBinaryExpressionOrThis as? KtElement ?: return null
                ImportPositionTypeAndReceiver.detect(diagnosticPsi)
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importPositionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>): Set<Name> {
        return (importPositionTypeAndReceiver.position as? KtSimpleNameExpression)?.mainReference?.resolvesByNames?.toSet().orEmpty()
    }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importPositionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val providers = getCandidateProvidersForUnresolvedNameReference(importPositionTypeAndReceiver)
        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        importPositionTypeAndReceiver: ImportPositionTypeAndReceiver<*, *>,
    ): Sequence<AbstractImportCandidatesProvider> = when (importPositionTypeAndReceiver) {
        is ImportPositionTypeAndReceiver.TypeReference -> sequenceOf(
            ClassifierImportCandidatesProvider(importPositionTypeAndReceiver),
        )

        is ImportPositionTypeAndReceiver.Annotation -> sequenceOf(
            AnnotationImportCandidatesProvider(importPositionTypeAndReceiver),
        )

        is ImportPositionTypeAndReceiver.DefaultCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionTypeAndReceiver),
            ClassifierImportCandidatesProvider(importPositionTypeAndReceiver),
            EnumEntryImportCandidatesProvider(importPositionTypeAndReceiver),
        )

        is ImportPositionTypeAndReceiver.DotCall,
        is ImportPositionTypeAndReceiver.SafeCall,
        is ImportPositionTypeAndReceiver.InfixCall,
        is ImportPositionTypeAndReceiver.OperatorCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionTypeAndReceiver),
        )

        is ImportPositionTypeAndReceiver.KDocNameReference -> sequenceOf(
            // TODO this is currently reported by KDocUnresolvedReferenceInspection
        )

        is ImportPositionTypeAndReceiver.CallableReference -> sequenceOf(
            CallableImportCandidatesProvider(importPositionTypeAndReceiver),
            ConstructorReferenceImportCandidatesProvider(importPositionTypeAndReceiver),
        )

        else -> sequenceOf()
    }
}