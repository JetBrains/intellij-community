// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.statistics.StatisticsInfo
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.renderer.base.annotations.KtRendererAnnotationsFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtCallableReturnTypeFilter
import org.jetbrains.kotlin.analysis.api.renderer.declarations.KtDeclarationRenderer
import org.jetbrains.kotlin.analysis.api.renderer.declarations.impl.KtDeclarationRendererForSource
import org.jetbrains.kotlin.analysis.api.renderer.declarations.modifiers.renderers.KtRendererVisibilityModifierProvider
import org.jetbrains.kotlin.analysis.api.symbols.*
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtNamedSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.analysis.utils.printer.prettyPrint
import org.jetbrains.kotlin.idea.actions.KotlinAddImportActionInfo
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
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.parameterInfo.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability
import org.jetbrains.kotlin.resolve.ImportPath
import javax.swing.Icon

class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    private val importVariants: List<AutoImportVariant>
) : QuickFixActionBase<KtElement>(element), HintAction {
    private data class SymbolBasedAutoImportVariant(
        override val fqName: FqName,
        override val declarationToImport: PsiElement?,
        override val icon: Icon?,
        override val debugRepresentation: String,
        val statisticsInfo: StatisticsInfo
    ) : AutoImportVariant {
        override val hint: String = fqName.asString()
    }

    init {
        require(importVariants.isNotEmpty())
    }

    override fun getText(): String = text

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is KtFile) return

        createAddImportAction(project, editor, file).execute()
    }

    private fun createAddImportAction(project: Project, editor: Editor, file: KtFile): QuestionAction {
        return ImportQuestionAction(project, editor, file, importVariants)
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
        val project = file.project

        val elementRange = element.textRange
        val autoImportHintText = KotlinBundle.message("fix.import.question", importVariants.first().fqName.asString())

        HintManager.getInstance().showQuestionHint(
            editor,
            autoImportHintText,
            elementRange.startOffset,
            elementRange.endOffset,
            createAddImportAction(project, editor, file)
        )

        return true
    }

    override fun fixSilently(editor: Editor): Boolean {
        val element = element ?: return false
        val file = element.containingKtFile
        if (!DaemonCodeAnalyzerSettings.getInstance().isImportHintEnabled) return false
        if (!ShowAutoImportPass.isAddUnambiguousImportsOnTheFlyEnabled(file)) return false
        val project = file.project
        val addImportAction = createAddImportAction(project, editor, file)
        if (importVariants.size == 1) {
            addImportAction.execute()
            return true
        } else {
            return false
        }
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

    override fun isAvailableImpl(project: Project, editor: Editor?, file: PsiFile): Boolean {
        return super.isAvailableImpl(project, editor, file) && !isOutdated(project)
    }

    private class ImportQuestionAction(
        private val project: Project,
        private val editor: Editor,
        private val file: KtFile,
        private val importVariants: List<AutoImportVariant>
    ) : QuestionAction {

        init {
            require(importVariants.isNotEmpty())
        }

        override fun execute(): Boolean {
            KotlinAddImportActionInfo.executeListener?.onExecute(importVariants)
            when (importVariants.size) {
                1 -> {
                    addImport(importVariants.single())
                    return true
                }

                0 -> {
                    return false
                }

                else -> {
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
        val FACTORY = diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) { diagnostic ->
            val element = diagnostic.psi

            val project = element.project
            val indexProvider = KtSymbolFromIndexProvider.create(project)

            val quickFix = when (element) {
                is KtTypeReference -> createImportTypeFix(indexProvider, element)
                is KtSimpleNameExpression -> createImportNameFix(indexProvider, element, element.getReferencedNameAsName())
                else -> null
            }

            listOfNotNull(quickFix)
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
            element: KtElement,
            importCandidateSymbols: List<KtDeclarationSymbol>,
        ): ImportQuickFix? {
            if (importCandidateSymbols.isEmpty()) return null

            val analyzerServices = element.containingKtFile.platform.findAnalyzerServices(element.project)
            val defaultImports = analyzerServices.getDefaultImports(element.languageVersionSettings, includeLowPriorityImports = true)
            val excludedImports = analyzerServices.excludedImports

            val isImported = { fqName: FqName -> ImportPath(fqName, isAllUnder = false).isImported(defaultImports, excludedImports) }
            val importPrioritizer = ImportPrioritizer(element.containingKtFile, isImported)
            val expressionImportWeigher = ExpressionImportWeigher.createWeigher(element)

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

            val sortedImportVariants = sortedImportCandidateSymbolsWithPriorities
                .map { (symbol, priority) ->
                    SymbolBasedAutoImportVariant(
                        symbol.getFqName(),
                        symbol.psi,
                        getIconFor(symbol),
                        renderSymbol(symbol),
                        priority.statisticsInfo,
                    )
                }

            return ImportQuickFix(element, text, sortedImportVariants)
        }

        context(KtAnalysisSession)
        private fun KtDeclarationSymbol.getImportKind(): ImportFixHelper.ImportKind? = when {
            this is KtPropertySymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_PROPERTY
            this is KtPropertySymbol -> ImportFixHelper.ImportKind.PROPERTY

            this is KtFunctionSymbol && isOperator -> ImportFixHelper.ImportKind.OPERATOR
            this is KtFunctionSymbol && isExtension -> ImportFixHelper.ImportKind.EXTENSION_FUNCTION
            this is KtFunctionSymbol -> ImportFixHelper.ImportKind.FUNCTION

            this is KtNamedClassOrObjectSymbol && classKind.isObject -> ImportFixHelper.ImportKind.OBJECT
            this is KtNamedClassOrObjectSymbol -> ImportFixHelper.ImportKind.CLASS

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

        context(KtAnalysisSession)
        fun createImportNameFix(
            indexProvider: KtSymbolFromIndexProvider,
            element: KtSimpleNameExpression,
            unresolvedName: Name
        ): ImportQuickFix? {
            val firFile = element.containingKtFile.getFileSymbol()

            val isVisible: (KtSymbol) -> Boolean =
                { it !is KtSymbolWithVisibility || isVisible(it, firFile, null, element) }

            val candidateSymbols = buildList {
                addAll(collectCallableCandidates(indexProvider, element, unresolvedName, isVisible))
                if (element.getReceiverExpression() == null) {
                    addAll(collectTypesCandidates(indexProvider, unresolvedName, isVisible))
                }
            }

            return createImportFix(element, candidateSymbols)
        }

        context(KtAnalysisSession)
        private fun createImportTypeFix(
            indexProvider: KtSymbolFromIndexProvider,
            element: KtTypeReference
        ): ImportQuickFix? {
            val firFile = element.containingKtFile.getFileSymbol()
            val unresolvedName = element.typeName ?: return null

            val isVisible: (KtNamedClassOrObjectSymbol) -> Boolean =
                { isVisible(it, firFile, null, element) }

            return createImportFix(element, collectTypesCandidates(indexProvider, unresolvedName, isVisible))
        }

        context(KtAnalysisSession)
        private fun collectCallableCandidates(
            indexProvider: KtSymbolFromIndexProvider,
            element: KtSimpleNameExpression,
            unresolvedName: Name,
            isVisible: (KtCallableSymbol) -> Boolean
        ): List<KtDeclarationSymbol> {
            val explicitReceiver = element.getReceiverExpression()

            val callablesCandidates = buildList {
                if (explicitReceiver == null) {
                    addAll(indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { declaration ->
                        // filter out extensions here, because they are added later with the use of information about receiver types
                        declaration.canBeImported() && !declaration.isExtensionDeclaration()
                    })
                    addAll(indexProvider.getJavaCallableSymbolsByName(unresolvedName) { it.canBeImported() })
                }

                val receiverTypes = collectReceiverTypesForElement(element, explicitReceiver)
                val isInfixFunctionExpected = (element.parent as? KtBinaryExpression)?.operationReference == element

                addAll(indexProvider.getTopLevelExtensionCallableSymbolsByName(unresolvedName, receiverTypes) { declaration ->
                    declaration.canBeImported() && (!isInfixFunctionExpected || declaration.hasModifier(KtTokens.INFIX_KEYWORD))
                })
            }

            return callablesCandidates
                .filter { isVisible(it) && it.callableIdIfNonLocal != null }
                .toList()
        }

        context(KtAnalysisSession)
        private fun collectTypesCandidates(
            indexProvider: KtSymbolFromIndexProvider,
            unresolvedName: Name,
            isVisible: (KtNamedClassOrObjectSymbol) -> Boolean
        ): List<KtNamedClassOrObjectSymbol> {
            val classesCandidates =
                indexProvider.getKotlinClassesByName(unresolvedName) { it.canBeImported() } +
                        indexProvider.getJavaClassesByName(unresolvedName) { it.canBeImported() }

            return classesCandidates
                .filter { isVisible(it) && it.classIdIfNonLocal != null }
                .toList()
        }
    }
}

private fun PsiMember.canBeImported(): Boolean {
    return when (this) {
        is PsiClass -> qualifiedName != null && (containingClass == null || hasModifier(JvmModifier.STATIC))
        is PsiField, is PsiMethod -> hasModifier(JvmModifier.STATIC) && containingClass?.qualifiedName != null
        else -> false
    }
}

private fun KtDeclaration.canBeImported(): Boolean {
    return when (this) {
        is KtProperty -> isTopLevel || containingClassOrObject is KtObjectDeclaration
        is KtNamedFunction -> isTopLevel || containingClassOrObject is KtObjectDeclaration
        is KtClassOrObject ->
            getClassId() != null && parentsOfType<KtClassOrObject>(withSelf = true).none { it.hasModifier(KtTokens.INNER_KEYWORD) }

        else -> false
    }
}

private val KtTypeReference.typeName: Name?
    get() {
        val userType = typeElement?.unwrapNullability() as? KtUserType
        return userType?.referencedName?.let(Name::identifier)
    }