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
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        return when (diagnostic) {
            is KaFirDiagnostic.UnresolvedImport,
            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver,
            is KaFirDiagnostic.InvisibleReference -> {
                val diagnosticPsi = diagnostic.psi.operationReferenceForBinaryExpressionOrThis as? KtElement ?: return null
                DefaultImportContext(diagnosticPsi, ImportPositionTypeAndReceiver.detect(diagnosticPsi))
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, importContext: ImportContext): Set<Name> {
        return (importContext.position as? KtSimpleNameExpression)?.mainReference?.resolvesByNames?.toSet().orEmpty()
    }

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        importContext: ImportContext,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        val providers = getCandidateProvidersForUnresolvedNameReference(importContext)
        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(_: KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        importContext: ImportContext,
    ): Sequence<AbstractImportCandidatesProvider> = when (importContext.positionType) {
        is ImportPositionType.TypeReference -> sequenceOf(
            ClassifierImportCandidatesProvider(importContext),
        )

        is ImportPositionType.Annotation -> sequenceOf(
            AnnotationImportCandidatesProvider(importContext),
        )

        is ImportPositionType.DefaultCall -> sequenceOf(
            CallableImportCandidatesProvider(importContext),
            ClassifierImportCandidatesProvider(importContext),
            EnumEntryImportCandidatesProvider(importContext),
        )

        is ImportPositionType.DotCall,
        is ImportPositionType.SafeCall,
        is ImportPositionType.InfixCall,
        is ImportPositionType.OperatorCall -> sequenceOf(
            CallableImportCandidatesProvider(importContext),
        )

        is ImportPositionType.KDocNameReference -> sequenceOf(
            // TODO this is currently reported by KDocUnresolvedReferenceInspection
        )

        is ImportPositionType.CallableReference -> sequenceOf(
            CallableImportCandidatesProvider(importContext),
            ConstructorReferenceImportCandidatesProvider(importContext),
        )

        else -> sequenceOf()
    }
}
