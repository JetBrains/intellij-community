// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.diagnostics.KaDiagnosticWithPsi
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.analysis.api.utils.getDefaultImports
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.util.isImported
import org.jetbrains.kotlin.idea.codeInsight.K2StatisticsInfoProvider
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories.DelegateMethodImportQuickFixFactory
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.factories.UnresolvedNameReferenceImportQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.ImportPath
import javax.swing.Icon

object ImportQuickFixProvider {
    context(KaSession)
    fun getFixes(diagnostic: KaDiagnosticWithPsi<*>): List<ImportQuickFix> {
        return getFixes(setOf(diagnostic))
    }

    context(KaSession)
    fun getFixes(diagnostics: Set<KaDiagnosticWithPsi<*>>): List<ImportQuickFix> {
        val factories = listOf(
            UnresolvedNameReferenceImportQuickFixFactory(),
            DelegateMethodImportQuickFixFactory(),
        )

        return factories.flatMap { it.run { createQuickFixes(diagnostics) } }
    }

    @KaExperimentalApi
    private val renderer: KaDeclarationRenderer = KaDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
        modifiersRenderer = modifiersRenderer.with {
            visibilityProvider = KaRendererVisibilityModifierProvider.WITH_IMPLICIT_VISIBILITY
        }
        annotationRenderer = annotationRenderer.with {
            annotationFilter = KaRendererAnnotationsFilter.NONE
        }
        returnTypeFilter = KaCallableReturnTypeFilter.ALWAYS
    }

    context(KaSession)
    private fun getIconFor(candidate: ImportCandidate): Icon? = getIconFor(candidate.symbol)

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun renderCandidate(candidate: ImportCandidate): String = prettyPrint {
        val fqName = candidate.getFqName()
        if (
            candidate.symbol is KaNamedClassSymbol || 
            candidate.symbol is KaEnumEntrySymbol
        ) {
            append("class $fqName")
        } else {
            renderer.renderDeclaration(useSiteSession, candidate.symbol, printer = this)
        }

        candidate.packageName?.let { packageName ->
            append(" defined in ${packageName.asString()}")
            candidate.psi?.containingFile?.let { append(" in file ${it.name}") }
        }
    }

    internal fun KaSession.createImportFix(
        position: KtElement,
        data: ImportData,
    ): ImportQuickFix? {
        val text = ImportFixHelper.calculateTextForFix(
            data.importsInfo,
            suggestions = data.uniqueFqNameSortedImportCandidates.map { (candidate, _) -> candidate.getFqName() }
        )
        return ImportQuickFix(position, text, data.importVariants)
    }

    context(KaSession)
    internal fun createImportData(
        position: KtElement,
        importCandidates: List<ImportCandidate>,
    ): ImportData? {
        if (importCandidates.isEmpty()) return null

        val containingKtFile = position.containingKtFile

        val defaultImports = containingKtFile.getDefaultImports(useSiteModule)

        val isImported = { fqName: FqName ->
            ImportPath(fqName, isAllUnder = false)
                .isImported(
                    defaultImports.defaultImports.map { it.importPath },
                    defaultImports.excludedFromDefaultImports.map { it.fqName }
                )
        }

        val importPrioritizer = ImportPrioritizer(containingKtFile, isImported)
        val expressionImportWeigher = ExpressionImportWeigher.createWeigher(position)

        val sortedImportCandidatesWithPriorities = importCandidates
            .map { it to createPriorityForImportCandidate(importPrioritizer, expressionImportWeigher, it) }
            .sortedBy { (_, priority) -> priority }

        val sortedImportInfos = sortedImportCandidatesWithPriorities.mapNotNull { (candidate, priority) ->
            val kind = candidate.getImportKind() ?: return@mapNotNull null
            val name = candidate.getImportName()
            ImportFixHelper.ImportInfo(kind, name, priority)
        }

        // for each distinct fqName, leave only the variant with the best priority to show in the popup 
        val uniqueFqNameSortedImportCandidates =
            sortedImportCandidatesWithPriorities.distinctBy { (candidate, _) -> candidate.getFqName() }


        val implicitReceiverTypes = containingKtFile.scopeContext(position).implicitReceivers.map { it.type }
        // don't import callable on the fly as it might be unresolved because of an erroneous implicit receiver
        val doNotImportCallablesOnFly = implicitReceiverTypes.any { it is KaErrorType }

        val sortedImportVariants = uniqueFqNameSortedImportCandidates
            .map { (candidate, priority) ->
                SymbolBasedAutoImportVariant(
                    candidate.createPointer(),
                    candidate.getFqName(),
                    candidate.psi,
                    getIconFor(candidate),
                    renderCandidate(candidate),
                    priority.statisticsInfo,
                    candidate.doNotImportOnTheFly(doNotImportCallablesOnFly),
                )
            }

        return ImportData(sortedImportVariants, sortedImportInfos, uniqueFqNameSortedImportCandidates)
    }

    internal data class ImportData(
        val importVariants: List<AutoImportVariant>,
        val importsInfo:  List<ImportFixHelper.ImportInfo<ImportPrioritizer.Priority>>,
        val uniqueFqNameSortedImportCandidates: List<Pair<ImportCandidate, ImportPrioritizer.Priority>>
    )

    context(KaSession)
    private fun ImportCandidate.doNotImportOnTheFly(doNotImportCallablesOnFly: Boolean): Boolean = when (this) {
        // don't import nested class on the fly because it will probably add qualification and confuse the user
        is ClassLikeImportCandidate -> symbol is KaNamedClassSymbol && symbol.isNested()
        is CallableImportCandidate -> doNotImportCallablesOnFly
    }

    context(KaSession)
    private fun KaNamedClassSymbol.isNested(): Boolean = containingSymbol is KaNamedClassSymbol

    context(KaSession)
    private fun ImportCandidate.getImportKind(): ImportFixHelper.ImportKind? = when (this) {
        is CallableImportCandidate -> when {
            symbol is KaPropertySymbol && symbol.isExtension -> ImportFixHelper.ImportKind.EXTENSION_PROPERTY
            symbol is KaPropertySymbol -> ImportFixHelper.ImportKind.PROPERTY
            symbol is KaJavaFieldSymbol -> ImportFixHelper.ImportKind.PROPERTY

            symbol is KaNamedFunctionSymbol && symbol.isOperator -> ImportFixHelper.ImportKind.OPERATOR
            symbol is KaNamedFunctionSymbol && symbol.isExtension && symbol.isInfix -> ImportFixHelper.ImportKind.INFIX_EXTENSION_FUNCTION
            symbol is KaNamedFunctionSymbol && symbol.isExtension -> ImportFixHelper.ImportKind.EXTENSION_FUNCTION
            symbol is KaNamedFunctionSymbol && symbol.isInfix -> ImportFixHelper.ImportKind.INFIX_FUNCTION
            symbol is KaNamedFunctionSymbol -> ImportFixHelper.ImportKind.FUNCTION
            
            symbol is KaEnumEntrySymbol -> ImportFixHelper.ImportKind.CLASS
            
            else -> null
        }

        is ClassLikeImportCandidate -> when {
            symbol is KaNamedClassSymbol && symbol.classKind.isObject -> ImportFixHelper.ImportKind.OBJECT
            symbol is KaNamedClassSymbol -> ImportFixHelper.ImportKind.CLASS
            symbol is KaTypeAliasSymbol -> ImportFixHelper.ImportKind.TYPE_ALIAS
            
            else -> null
        }
    }

    context(KaSession)
    private fun ImportCandidate.getImportName(): String = buildString {
        if (
            this@getImportName is CallableImportCandidate && 
            symbol !is KaEnumEntrySymbol
        ) {
            val classSymbol = when {
                receiverType != null -> receiverType?.expandedSymbol
                else -> containingClass
            }
            classSymbol?.name?.let { append(it.asString()) }
        }

        if (this.isNotEmpty()) append('.')
        append(name.asString())
    }

    context(KaSession)
    private fun ImportCandidate.getFqName(): FqName =
        fqName ?: error("Unexpected null for fully-qualified name of importable symbol")

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createPriorityForImportCandidate(
        prioritizer: ImportPrioritizer,
        expressionImportWeigher: ExpressionImportWeigher,
        candidate: ImportCandidate
    ): ImportPrioritizer.Priority =
        prioritizer.Priority(
            declaration = candidate.psi,
            // TODO consider passing whole candidate to avoid loosing information 
            statisticsInfo = K2StatisticsInfoProvider.forDeclarationSymbol(candidate.symbol),
            isDeprecated = candidate.deprecationStatus != null,
            fqName = candidate.getFqName(),
            // TODO consider passing whole candidate to avoid loosing information 
            expressionWeight = expressionImportWeigher.weigh(candidate.symbol),
        )
}