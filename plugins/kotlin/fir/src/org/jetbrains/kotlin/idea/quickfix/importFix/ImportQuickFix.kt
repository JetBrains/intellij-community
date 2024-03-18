// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.importFix

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.types.KtErrorType
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.actions.KotlinAddImportActionInfo.executeListener
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinIconProvider.getIconFor
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.base.projectStructure.compositeAnalysis.findAnalyzerServices
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.utils.fqname.isImported
import org.jetbrains.kotlin.idea.codeInsight.K2StatisticsInfoProvider
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.idea.util.positionContext.*
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.ImportPath
import javax.swing.Icon

class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    private val importVariants: List<AutoImportVariant>
) : KotlinImportQuickFixAction<KtElement>(element), HintAction, HighPriorityAction {
    private data class SymbolBasedAutoImportVariant(
        override val fqName: FqName,
        override val declarationToImport: PsiElement?,
        override val icon: Icon?,
        override val debugRepresentation: String,
        val statisticsInfo: StatisticsInfo,
        val canNotBeImportedOnTheFly: Boolean,
    ) : AutoImportVariant {
        override val hint: String = fqName.asString()
    }

    init {
        require(importVariants.isNotEmpty())
    }

    override fun getText(): String = text

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (editor == null) return

        createImportAction(editor, file)?.execute()
    }

    override fun createImportAction(editor: Editor, file: KtFile): QuestionAction? =
        if (element != null) ImportQuestionAction(file.project, editor, file, importVariants) else null

    override fun createAutoImportAction(
        editor: Editor,
        file: KtFile,
        filterSuggestions: (Collection<FqName>) -> Collection<FqName>
    ): QuestionAction? {
        val filteredFqNames = filterSuggestions(importVariants.map { it.fqName }).toSet()
        if (filteredFqNames.size != 1) return null

        val singleSuggestion = importVariants.filter { it.fqName in filteredFqNames }.first()
        if ((singleSuggestion as SymbolBasedAutoImportVariant).canNotBeImportedOnTheFly) return null

        return ImportQuestionAction(file.project, editor, file, listOf(singleSuggestion), onTheFly = true)
    }

    override fun showHint(editor: Editor): Boolean {
        val element = element ?: return false
        if (
            ApplicationManager.getApplication().isHeadlessEnvironment
            || HintManager.getInstance().hasShownHintsThatWillHideByOtherHint(true)
        ) {
            return false
        }

        val file = element.containingKtFile

        val elementRange = element.textRange
        val autoImportHintText = KotlinBundle.message("fix.import.question", importVariants.first().fqName.asString())
        val importAction = createImportAction(editor, file) ?: return false

        HintManager.getInstance().showQuestionHint(
            editor,
            autoImportHintText,
            elementRange.startOffset,
            elementRange.endOffset,
            importAction,
        )

        return true
    }

    private val modificationCountOnCreate: Long = PsiModificationTracker.getInstance(element.project).modificationCount

    /**
     * This is a safe-guard against showing hint after the quickfix have been applied.
     *
     * Inspired by the org.jetbrains.kotlin.idea.quickfix.ImportFixBase.isOutdated
     */
    private fun isOutdated(project: Project): Boolean {
        return modificationCountOnCreate != PsiModificationTracker.getInstance(project).modificationCount
    }

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean =
        !isOutdated(project)

    private class ImportQuestionAction(
        private val project: Project,
        private val editor: Editor,
        private val file: KtFile,
        private val importVariants: List<AutoImportVariant>,
        private val onTheFly: Boolean = false,
    ) : QuestionAction {

        init {
            require(importVariants.isNotEmpty())
        }

        override fun execute(): Boolean {
            file.executeListener?.onExecute(importVariants)
            when (importVariants.size) {
                1 -> {
                    addImport(importVariants.single())
                    return true
                }

                0 -> {
                    return false
                }

                else -> {
                    if (onTheFly) return false

                    if (ApplicationManager.getApplication().isUnitTestMode) {
                        addImport(importVariants.first())
                        return true
                    }
                    ImportFixHelper.createListPopupWithImportVariants(project, importVariants, ::addImport).showInBestPositionFor(editor)

                    return true
                }
            }
        }

        private fun addImport(importVariant: AutoImportVariant) {
            require(importVariant is SymbolBasedAutoImportVariant)

            StatisticsManager.getInstance().incUseCount(importVariant.statisticsInfo)

            project.executeWriteCommand(QuickFixBundle.message("add.import")) {
                file.addImport(importVariant.fqName)
            }
        }
    }

    companion object {
        val invisibleReferenceFactory = diagnosticFixFactory(KtFirDiagnostic.InvisibleReference::class) { getFixes(it.psi) }

        // this factory is used only for importing references on the fly; in all other cases import fixes for unresolved references
        // are created by [org.jetbrains.kotlin.idea.codeInsight.KotlinFirUnresolvedReferenceQuickFixProvider]
        val unresolvedReferenceFactory = diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) { getFixes(it.psi) }

        context(KtAnalysisSession)
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
                    is KDocParameterNamePositionContext -> {
                    }
                }
            }

            val candidates = candidateProviders.flatMap { it.collectCandidates() }
            val quickFix = createImportFix(positionContext.nameExpression, candidates)

            return listOfNotNull(quickFix)
        }

        private val renderer: KtDeclarationRenderer = KtDeclarationRendererForSource.WITH_QUALIFIED_NAMES.with {
            modifiersRenderer = modifiersRenderer.with {
                visibilityProvider = KtRendererVisibilityModifierProvider.WITH_IMPLICIT_VISIBILITY
            }
            annotationRenderer = annotationRenderer.with {
                annotationFilter = KtRendererAnnotationsFilter.NONE
            }
            returnTypeFilter = KtCallableReturnTypeFilter.ALWAYS
        }


        context(KtAnalysisSession)
        private fun renderSymbol(symbol: KtDeclarationSymbol): String = prettyPrint {
            val fqName = symbol.getFqName()
            if (symbol is KtNamedClassOrObjectSymbol) {
                append("class $fqName")
            } else {
                renderer.renderDeclaration(symbol, printer = this)
            }

            when (symbol) {
                is KtCallableSymbol -> symbol.callableIdIfNonLocal?.packageName
                is KtClassLikeSymbol -> symbol.classIdIfNonLocal?.packageFqName
                else -> null
            }?.let { packageName ->
                append(" defined in ${packageName.asString()}")
                symbol.psi?.containingFile?.let { append(" in file ${it.name}") }
            }
        }

        context(KtAnalysisSession)
        private fun createImportFix(
            position: KtElement,
            importCandidateSymbols: List<KtDeclarationSymbol>,
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

            val implicitReceiverTypes = containingKtFile.getScopeContextForPosition(position).implicitReceivers.map { it.type }
            // don't import callable on the fly as it might be unresolved because of an erroneous implicit receiver
            val doNotImportCallablesOnFly = implicitReceiverTypes.any { it is KtErrorType }

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

        context(KtAnalysisSession)
        private fun KtDeclarationSymbol.doNotImportOnTheFly(doNotImportCallablesOnFly: Boolean): Boolean = when (this) {
            // don't import nested class on the fly because it will probably add qualification and confuse the user
            is KtNamedClassOrObjectSymbol -> isNested()
            is KtCallableSymbol -> doNotImportCallablesOnFly
            else -> false
        }

        context(KtAnalysisSession)
        private fun KtNamedClassOrObjectSymbol.isNested(): Boolean = getContainingSymbol() is KtNamedClassOrObjectSymbol

        context(KtAnalysisSession)
        private fun KtDeclarationSymbol.getImportKind(): ImportFixHelper.ImportKind? = when {
            this is KtPropertySymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_PROPERTY
            this is KtPropertySymbol -> ImportFixHelper.ImportKind.PROPERTY
            this is KtJavaFieldSymbol -> ImportFixHelper.ImportKind.PROPERTY

            this is KtFunctionSymbol && isOperator -> ImportFixHelper.ImportKind.OPERATOR
            this is KtFunctionSymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_FUNCTION
            this is KtFunctionSymbol -> ImportFixHelper.ImportKind.FUNCTION

            this is KtNamedClassOrObjectSymbol && classKind.isObject -> ImportFixHelper.ImportKind.OBJECT
            this is KtNamedClassOrObjectSymbol -> ImportFixHelper.ImportKind.CLASS
            this is KtTypeAliasSymbol -> ImportFixHelper.ImportKind.TYPE_ALIAS

            else -> null
        }

        context(KtAnalysisSession)
        private fun KtDeclarationSymbol.getImportName(): String = buildString {
            if (this@getImportName !is KtNamedSymbol) error("Unexpected anonymous declaration")

            if (this@getImportName is KtCallableSymbol) {
                val classSymbol = if (receiverType != null) receiverType?.expandedClassSymbol else originalContainingClassForOverride
                classSymbol?.name?.let { append(it.asString()) }
            }

            if (this.isNotEmpty()) append('.')
            append(name.asString())
        }

        context(KtAnalysisSession)
        private fun KtDeclarationSymbol.getFqName(): FqName =
            getFqNameIfPackageOrNonLocal() ?: error("Unexpected null for fully-qualified name of importable symbol")

        context(KtAnalysisSession)
        private fun createPriorityForImportableSymbol(
            prioritizer: ImportPrioritizer,
            expressionImportWeigher: ExpressionImportWeigher,
            symbol: KtDeclarationSymbol
        ): ImportPrioritizer.Priority =
            prioritizer.Priority(
                declaration = symbol.psi,
                statisticsInfo = K2StatisticsInfoProvider.forDeclarationSymbol(symbol),
                isDeprecated = symbol.deprecationStatus != null,
                fqName = symbol.getFqName(),
                expressionWeight = expressionImportWeigher.weigh(symbol),
            )
    }
}