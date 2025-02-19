// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.highlighter.operationReferenceForBinaryExpressionOrThis
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtElement

internal class UnresolvedNameReferenceImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, KotlinRawPositionContext>? {
        return when (diagnostic) {
            is KaFirDiagnostic.UnresolvedImport,
            is KaFirDiagnostic.UnresolvedReference,
            is KaFirDiagnostic.UnresolvedReferenceWrongReceiver,
            is KaFirDiagnostic.InvisibleReference -> {
                val diagnosticPsi = diagnostic.psi.operationReferenceForBinaryExpressionOrThis
                val position = diagnosticPsi.containingFile.findElementAt(diagnosticPsi.startOffset)
                val positionContext = position?.let { KotlinPositionContextDetector.detect(it) } as? KotlinNameReferencePositionContext
                    ?: return null
                return positionContext.nameExpression to positionContext
            }

            else -> null
        }
    }

    override fun provideUnresolvedNames(diagnostic: KaDiagnosticWithPsi<*>, positionContext: KotlinRawPositionContext): Set<Name> =
        (positionContext as? KotlinNameReferencePositionContext)?.reference?.resolvesByNames?.toSet().orEmpty()

    override fun KaSession.provideImportCandidates(
        unresolvedName: Name,
        positionContext: KotlinRawPositionContext,
        indexProvider: KtSymbolFromIndexProvider
    ): List<ImportCandidate> {
        if (positionContext !is KotlinNameReferencePositionContext) return emptyList()
        val providers = getCandidateProvidersForUnresolvedNameReference(positionContext)
        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        positionContext: KotlinNameReferencePositionContext,
    ): Sequence<AbstractImportCandidatesProvider> = when (positionContext) {
        is KotlinSuperTypeCallNameReferencePositionContext,
        is KotlinTypeNameReferencePositionContext
            -> sequenceOf(
            ClassifierImportCandidatesProvider(positionContext),
        )

        is KotlinAnnotationTypeNameReferencePositionContext -> sequenceOf(
            AnnotationImportCandidatesProvider(positionContext),
        )

        is KotlinWithSubjectEntryPositionContext,
        is KotlinExpressionNameReferencePositionContext
            -> sequenceOf(
            CallableImportCandidatesProvider(positionContext),
            ClassifierImportCandidatesProvider(positionContext),
            EnumEntryImportCandidatesProvider(positionContext),
        )

        is KotlinInfixCallPositionContext,
        is KotlinOperatorCallPositionContext
            -> sequenceOf(
            CallableImportCandidatesProvider(positionContext),
        )

        is KDocLinkNamePositionContext -> sequenceOf(
            // TODO
        )

        is KotlinCallableReferencePositionContext -> sequenceOf(
            CallableImportCandidatesProvider(positionContext),
            ConstructorReferenceImportCandidatesProvider(positionContext),
        )

        is KotlinImportDirectivePositionContext,
        is KotlinPackageDirectivePositionContext,
        is KotlinSuperReceiverNameReferencePositionContext,
        is KotlinLabelReferencePositionContext,
        is KDocParameterNamePositionContext
            -> sequenceOf()
    }
}