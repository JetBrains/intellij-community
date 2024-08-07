// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KaRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KaDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KaDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KaRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KaNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KaErrorType
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.codeInsight.K2StatisticsInfoProvider
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.resolve.ImportPath

object ImportQuickFixProvider {
    context(KaSession)
    fun getFixes(diagnosticPsi: PsiElement): List<ImportQuickFix> {
        val position = diagnosticPsi.containingFile.findElementAt(diagnosticPsi.startOffset)
        val positionContext = position?.let { KotlinPositionContextDetector.detect(it) }

        if (positionContext !is KotlinNameReferencePositionContext) return emptyList()

        val indexProvider = KtSymbolFromIndexProvider.createForElement(positionContext.nameExpression)
        val candidateProviders = buildList {
            when (positionContext) {
                is KotlinSuperTypeCallNameReferencePositionContext,
                is KotlinTypeNameReferencePositionContext -> {
                    add(ClassifierImportCandidatesProvider(positionContext, indexProvider))
                }

                is KotlinAnnotationTypeNameReferencePositionContext -> {
                    add(AnnotationImportCandidatesProvider(positionContext, indexProvider))
                }

                is KotlinWithSubjectEntryPositionContext,
                is KotlinExpressionNameReferencePositionContext -> {
                    add(CallableImportCandidatesProvider(positionContext, indexProvider))
                    add(ClassifierImportCandidatesProvider(positionContext, indexProvider))
                }

                is KotlinInfixCallPositionContext -> {
                    add(InfixCallableImportCandidatesProvider(positionContext, indexProvider))
                }

                is KDocLinkNamePositionContext -> {
                    // TODO
                }

                is KotlinCallableReferencePositionContext -> {
                    add(CallableImportCandidatesProvider(positionContext, indexProvider))
                    add(ConstructorReferenceImportCandidatesProvider(positionContext, indexProvider))
                }

                is KotlinImportDirectivePositionContext,
                is KotlinPackageDirectivePositionContext,
                is KotlinSuperReceiverNameReferencePositionContext,
                is KotlinLabelReferencePositionContext,
                is KDocParameterNamePositionContext -> {
                }
            }
        }

        val candidates = candidateProviders.flatMap { it.collectCandidates() }
        val quickFix = createImportFix(positionContext.nameExpression, candidates)

        return listOfNotNull(quickFix)
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
    @OptIn(KaExperimentalApi::class)
    private fun renderSymbol(symbol: KaDeclarationSymbol): String = prettyPrint {
        val fqName = symbol.getFqName()
        if (symbol is KaNamedClassSymbol) {
            append("class $fqName")
        } else {
            renderer.renderDeclaration(useSiteSession, symbol, printer = this)
        }

        when (symbol) {
            is KaCallableSymbol -> symbol.callableId?.packageName
            is KaClassLikeSymbol -> symbol.classId?.packageFqName
            else -> null
        }?.let { packageName ->
            append(" defined in ${packageName.asString()}")
            symbol.psi?.containingFile?.let { append(" in file ${it.name}") }
        }
    }

    context(KaSession)
    private fun createImportFix(
        position: KtElement,
        importCandidateSymbols: List<KaDeclarationSymbol>,
    ): ImportQuickFix? {
        if (importCandidateSymbols.isEmpty()) return null

        val containingKtFile = position.containingKtFile

        val analyzerServices = containingKtFile.platform.findAnalyzerServices(position.project)
        val defaultImports = analyzerServices.getDefaultImports(position.languageVersionSettings, includeLowPriorityImports = true)
        val excludedImports = analyzerServices.excludedImports

        val isImported = { fqName: FqName -> ImportPath(fqName, isAllUnder = false).isImported(defaultImports, excludedImports) }
        val importPrioritizer = ImportPrioritizer(containingKtFile, isImported)
        val expressionImportWeigher = ExpressionImportWeigher.createWeigher(position)

        val sortedImportCandidateSymbolsWithPriorities = importCandidateSymbols
            .map { it to createPriorityForImportableSymbol(importPrioritizer, expressionImportWeigher, it) }
            .sortedBy { (_, priority) -> priority }

        val sortedImportInfos = sortedImportCandidateSymbolsWithPriorities.mapNotNull { (candidateSymbol, priority) ->
            val kind = candidateSymbol.getImportKind() ?: return@mapNotNull null
            val name = candidateSymbol.getImportName()
            ImportFixHelper.ImportInfo(kind, name, priority)
        }

        val text = ImportFixHelper.calculateTextForFix(
            sortedImportInfos,
            suggestions = sortedImportCandidateSymbolsWithPriorities.map { (symbol, _) -> symbol.getFqName() }.distinct()
        )

        val implicitReceiverTypes = containingKtFile.scopeContext(position).implicitReceivers.map { it.type }
        // don't import callable on the fly as it might be unresolved because of an erroneous implicit receiver
        val doNotImportCallablesOnFly = implicitReceiverTypes.any { it is KaErrorType }

        val sortedImportVariants = sortedImportCandidateSymbolsWithPriorities
            .map { (symbol, priority) ->
                SymbolBasedAutoImportVariant(
                    symbol.getFqName(),
                    symbol.psi,
                    getIconFor(symbol),
                    renderSymbol(symbol),
                    priority.statisticsInfo,
                    symbol.doNotImportOnTheFly(doNotImportCallablesOnFly),
                )
            }

        return ImportQuickFix(position, text, sortedImportVariants)
    }

    context(KaSession)
    private fun KaDeclarationSymbol.doNotImportOnTheFly(doNotImportCallablesOnFly: Boolean): Boolean = when (this) {
        // don't import nested class on the fly because it will probably add qualification and confuse the user
        is KaNamedClassSymbol -> isNested()
        is KaCallableSymbol -> doNotImportCallablesOnFly
        else -> false
    }

    context(KaSession)
    private fun KaNamedClassSymbol.isNested(): Boolean = containingSymbol is KaNamedClassSymbol

    context(KaSession)
    private fun KaDeclarationSymbol.getImportKind(): ImportFixHelper.ImportKind? = when {
        this is KaPropertySymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_PROPERTY
        this is KaPropertySymbol -> ImportFixHelper.ImportKind.PROPERTY
        this is KaJavaFieldSymbol -> ImportFixHelper.ImportKind.PROPERTY

        this is KaNamedFunctionSymbol && isOperator -> ImportFixHelper.ImportKind.OPERATOR
        this is KaNamedFunctionSymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_FUNCTION
        this is KaNamedFunctionSymbol -> ImportFixHelper.ImportKind.FUNCTION

        this is KaNamedClassSymbol && classKind.isObject -> ImportFixHelper.ImportKind.OBJECT
        this is KaNamedClassSymbol -> ImportFixHelper.ImportKind.CLASS
        this is KaTypeAliasSymbol -> ImportFixHelper.ImportKind.TYPE_ALIAS

        else -> null
    }

    context(KaSession)
    private fun KaDeclarationSymbol.getImportName(): String = buildString {
        if (this@getImportName !is KaNamedSymbol) error("Unexpected anonymous declaration")

        if (this@getImportName is KaCallableSymbol) {
            val classSymbol = when {
                receiverType != null -> receiverType?.expandedSymbol
                else -> fakeOverrideOriginal.containingSymbol as? KaClassSymbol
            }
            classSymbol?.name?.let { append(it.asString()) }
        }

        if (this.isNotEmpty()) append('.')
        append(name.asString())
    }

    context(KaSession)
    private fun KaDeclarationSymbol.getFqName(): FqName =
        getFqNameIfPackageOrNonLocal() ?: error("Unexpected null for fully-qualified name of importable symbol")

    context(KaSession)
    @OptIn(KaExperimentalApi::class)
    private fun createPriorityForImportableSymbol(
        prioritizer: ImportPrioritizer,
        expressionImportWeigher: ExpressionImportWeigher,
        symbol: KaDeclarationSymbol
    ): ImportPrioritizer.Priority =
        prioritizer.Priority(
            declaration = symbol.psi,
            statisticsInfo = K2StatisticsInfoProvider.forDeclarationSymbol(symbol),
            isDeprecated = symbol.deprecationStatus != null,
            fqName = symbol.getFqName(),
            expressionWeight = expressionImportWeigher.weigh(symbol),
        )
}