// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

internal object MismatchedArgumentsImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): Pair<KtElement, ImportPositionContext<*, *>>? {
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

                val importPositionContext = ImportPositionContext.detect(adjustedDiagnosticPsi)
                importPositionContext.position to importPositionContext
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

        // TODO add applicability check here, see KTIJ-33214

        return providers.flatMap { it.collectCandidates(unresolvedName, indexProvider) }.toList()
    }

    context(KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        importPositionContext: ImportPositionContext<*, *>,
    ): Sequence<AbstractImportCandidatesProvider> = when (importPositionContext) {
        is ImportPositionContext.DefaultCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionContext),
            ClassifierImportCandidatesProvider(importPositionContext),
        )

        is ImportPositionContext.DotCall,
        is ImportPositionContext.SafeCall,
        is ImportPositionContext.InfixCall,
        is ImportPositionContext.OperatorCall -> sequenceOf(
            CallableImportCandidatesProvider(importPositionContext),
        )

        else -> sequenceOf()
    }
}
