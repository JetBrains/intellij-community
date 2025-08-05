// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.ReadActionCachedValue
import com.intellij.psi.util.parentOfType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.resolution.KaSuccessCallInfo
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.imports.KtFileWithReplacedImports
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.*
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories.MismatchedArgumentsImportQuickFixFactory.FILE_WITH_REPLACED_IMPORTS_CACHE
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getPossiblyQualifiedCallExpression

internal object MismatchedArgumentsImportQuickFixFactory : AbstractImportQuickFixFactory() {
    override fun KaSession.detectPositionContext(diagnostic: KaDiagnosticWithPsi<*>): ImportContext? {
        return when (diagnostic) {
            is KaFirDiagnostic.TooManyArguments,
            is KaFirDiagnostic.NoValueForParameter,
            is KaFirDiagnostic.ArgumentTypeMismatch,
            is KaFirDiagnostic.NamedParameterNotFound,
            is KaFirDiagnostic.NoneApplicable,
            is KaFirDiagnostic.WrongNumberOfTypeArguments,
            is KaFirDiagnostic.CannotInferParameterType -> {

                val originalDiagnosticPsi = diagnostic.psi

                val adjustedDiagnosticPsi = when {
                    originalDiagnosticPsi is KtOperationReferenceExpression -> originalDiagnosticPsi

                    // those diagnostics have the whole call expression as their PSI
                    diagnostic is KaFirDiagnostic.WrongNumberOfTypeArguments || diagnostic is KaFirDiagnostic.NoValueForParameter ->
                        (originalDiagnosticPsi as? KtExpression)?.getPossiblyQualifiedCallExpression()?.calleeExpression

                    else -> originalDiagnosticPsi.parentOfType<KtCallExpression>()?.calleeExpression
                } ?: return null

                DefaultImportContext(adjustedDiagnosticPsi, ImportPositionTypeAndReceiver.detect(adjustedDiagnosticPsi))
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

        return providers
            .flatMap { it.collectCandidates(unresolvedName, indexProvider) }
            .filter { candidate -> resolvesWithoutErrors(importContext.position, candidate) }
            .toList()
    }

    context(_: KaSession)
    private fun getCandidateProvidersForUnresolvedNameReference(
        importContext: ImportContext,
    ): Sequence<AbstractImportCandidatesProvider> = when (importContext.positionType) {
        is ImportPositionType.DefaultCall -> sequenceOf(
            CallableImportCandidatesProvider(importContext),
            ClassifierImportCandidatesProvider(importContext),
        )

        is ImportPositionType.DotCall,
        is ImportPositionType.SafeCall,
        is ImportPositionType.InfixCall,
        is ImportPositionType.OperatorCall -> sequenceOf(
            CallableImportCandidatesProvider(importContext),
        )

        else -> sequenceOf()
    }

    /**
     * Checks if the [originalCallExpression] resolves without any errors 
     * if the [candidate] is imported to the file.
     * 
     * Does in-the-air resolution with [KtFileWithReplacedImports], so can be expensive.
     */    
    context(_: KaSession)
    private fun resolvesWithoutErrors(originalCallExpression: KtElement, candidate: ImportCandidate): Boolean {
        if (!Registry.`is`("kotlin.k2.auto.import.mismatched.arguments.factory.applicability.filter.enabled")) {
            // do not do any filtering, let all candidates pass
            return true
        }

        val containingFile = originalCallExpression.containingKtFile

        if (containingFile is KtCodeFragment) {
            // KtFileWithReplacedImports does not properly work with KtCodeFragments now,
            // so we do not do actual applicability filtering.
            // That way, users can at least import something.
            // Should be implemented in KTIJ-33606
            return true
        }

        val candidateFqName = candidate.fqName ?: return false

        val fileWithReplacedImports = getFileWithReplacedImportsFor(containingFile)
        val copyCallExpression = fileWithReplacedImports.findMatchingElement(originalCallExpression) ?: return false

        return fileWithReplacedImports.withExtraImport(candidateFqName) {
            fileWithReplacedImports.analyze {
                val copyCallInfo = copyCallExpression.resolveToCall()

                copyCallInfo is KaSuccessCallInfo
            }
        }
    }

    private val FILE_WITH_REPLACED_IMPORTS_CACHE: ReadActionCachedValue<MutableMap<KtFile, KtFileWithReplacedImports>> =
        ReadActionCachedValue { mutableMapOf() }

    /**
     * Returns a matching [KtFileWithReplacedImports] for the [originalFile].
     * 
     * [KtFileWithReplacedImports] is cached via [FILE_WITH_REPLACED_IMPORTS_CACHE] to
     * avoid creating multiple copies of a singe [KtFile] during a single read action.
     */
    private fun getFileWithReplacedImportsFor(originalFile: KtFile): KtFileWithReplacedImports =
        FILE_WITH_REPLACED_IMPORTS_CACHE.getCachedOrEvaluate().getOrPut(originalFile) { 
            KtFileWithReplacedImports.createFrom(originalFile) 
        }
}
