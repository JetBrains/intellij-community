// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.AbstractImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.CallableImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ClassifierImportCandidatesProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.ImportCandidate
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtOperationReferenceExpression
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

internal object MismatchedArgumentsImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, KotlinRawPositionContext>? {
        return when (diagnostic) {
            is KaFirDiagnostic.TooManyArguments,
            is KaFirDiagnostic.NoValueForParameter,
            is KaFirDiagnostic.ArgumentTypeMismatch,
            is KaFirDiagnostic.NamedParameterNotFound,
            is KaFirDiagnostic.NoneApplicable,
            is KaFirDiagnostic.WrongNumberOfTypeArguments,
            is KaFirDiagnostic.NewInferenceNoInformationForParameter -> {
                
                val originalDiagnosticPsi = diagnostic.psi

                val adjustedDiagnosticPsi = when {
                    originalDiagnosticPsi is KtOperationReferenceExpression -> originalDiagnosticPsi

                    // those diagnostics have the whole call expression as their PSI
                    diagnostic is KaFirDiagnostic.WrongNumberOfTypeArguments || diagnostic is KaFirDiagnostic.NoValueForParameter ->
                        (originalDiagnosticPsi as? KtExpression)?.getPossiblyQualifiedCallExpression()?.calleeExpression

                    else -> originalDiagnosticPsi.parentOfType<KtCallExpression>()?.calleeExpression
                } ?: return null

                val position = adjustedDiagnosticPsi.containingFile.findElementAt(adjustedDiagnosticPsi.startOffset)
                val positionContext = position?.let { KotlinPositionContextDetector.detect(it) } as? KotlinNameReferencePositionContext
                    ?: return null
                positionContext.nameExpression to positionContext
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

        // TODO add applicability check here, see KTIJ-33214

        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        positionContext: KotlinNameReferencePositionContext,
    ): Sequence<AbstractImportCandidatesProvider> = when (positionContext) {
        is KotlinWithSubjectEntryPositionContext,
        is KotlinExpressionNameReferencePositionContext
            -> sequenceOf(
            CallableImportCandidatesProvider(positionContext),
            ClassifierImportCandidatesProvider(positionContext),
        )

        is KotlinInfixCallPositionContext,
        is KotlinOperatorCallPositionContext
            -> sequenceOf(
            CallableImportCandidatesProvider(positionContext),
        )

        is KotlinAnnotationTypeNameReferencePositionContext,
        is KotlinCallableReferencePositionContext,
        is KotlinSuperTypeCallNameReferencePositionContext,
        is KotlinTypeNameReferencePositionContext,
        is KotlinImportDirectivePositionContext,
        is KotlinPackageDirectivePositionContext,
        is KotlinSuperReceiverNameReferencePositionContext,
        is KotlinLabelReferencePositionContext,
        is KDocLinkNamePositionContext,
        is KDocParameterNamePositionContext
            -> sequenceOf()
    }
}
