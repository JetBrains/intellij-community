// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.HighPriorityAction
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
import com.intellij.psi.util.parentOfType
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
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.codeinsight.utils.getFqNameIfPackageOrNonLocal
import org.jetbrains.kotlin.idea.base.analysis.api.utils.collectReceiverTypesForElement
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.quickfix.ImportPrioritizer
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getReceiverExpression
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration
import org.jetbrains.kotlin.resolve.ImportPath
import javax.swing.Icon

class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    private val importVariants: List<AutoImportVariant>
) : QuickFixActionBase<KtElement>(element), HintAction, HighPriorityAction {
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

        context(KtAnalysisSession)
        fun getFixes(diagnosticPsi: PsiElement): List<ImportQuickFix> {
            val position = diagnosticPsi.findDescendantOfType<KtSimpleNameExpression> { it.textRange == diagnosticPsi.textRange }
                ?: return emptyList()

            val indexProvider = KtSymbolFromIndexProvider.createForElement(position)

            val quickFix = if (position.parentOfType<KtTypeReference>() != null) {
                createImportTypeFix(indexProvider, position)
            } else {
                createImportNameFix(indexProvider, position, position.getReferencedNameAsName())
            }

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

            val analyzerServices = position.containingKtFile.platform.findAnalyzerServices(position.project)
            val defaultImports = analyzerServices.getDefaultImports(position.languageVersionSettings, includeLowPriorityImports = true)
            val excludedImports = analyzerServices.excludedImports

            val isImported = { fqName: FqName -> ImportPath(fqName, isAllUnder = false).isImported(defaultImports, excludedImports) }
            val importPrioritizer = ImportPrioritizer(position.containingKtFile, isImported)
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

            return ImportQuickFix(position, text, sortedImportVariants)
        }

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
            position: KtSimpleNameExpression,
            unresolvedName: Name
        ): ImportQuickFix? {
            val firFile = position.containingKtFile.getFileSymbol()

            val isVisible: (KtSymbol) -> Boolean =
                { it !is KtSymbolWithVisibility || isVisible(it, firFile, receiverExpression = null, position) }

            val candidateSymbols = buildList {
                addAll(collectCallableCandidates(indexProvider, position, unresolvedName, isVisible))
                addAll(collectTypesCandidates(indexProvider, position, unresolvedName, isVisible))
            }

            return createImportFix(position, candidateSymbols)
        }

        context(KtAnalysisSession)
        private fun createImportTypeFix(
            indexProvider: KtSymbolFromIndexProvider,
            position: KtSimpleNameExpression,
        ): ImportQuickFix? {
            val firFile = position.containingKtFile.getFileSymbol()
            val unresolvedName = position.getReferencedNameAsName()

            val isVisible: (KtClassLikeSymbol) -> Boolean =
                { it is KtSymbolWithVisibility && isVisible(it, firFile, receiverExpression = null, position) }

            return createImportFix(position, collectTypesCandidates(indexProvider, position, unresolvedName, isVisible))
        }

        context(KtAnalysisSession)
        private fun collectCallableCandidates(
            indexProvider: KtSymbolFromIndexProvider,
            position: KtSimpleNameExpression,
            unresolvedName: Name,
            isVisible: (KtCallableSymbol) -> Boolean
        ): List<KtDeclarationSymbol> {
            val explicitReceiver = position.getReceiverExpression()

            val callablesCandidates = buildList {
                if (explicitReceiver == null) {
                    addAll(indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { declaration ->
                        // filter out extensions here, because they are added later with the use of information about receiver types
                        declaration.canBeImported() && !declaration.isExtensionDeclaration()
                    })
                    addAll(indexProvider.getJavaCallableSymbolsByName(unresolvedName) { it.canBeImported() })
                }

                val receiverTypes = collectReceiverTypesForElement(position, explicitReceiver)
                val isInfixFunctionExpected = (position.parent as? KtBinaryExpression)?.operationReference == position

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
            position: KtSimpleNameExpression,
            unresolvedName: Name,
            isVisible: (KtClassLikeSymbol) -> Boolean
        ): List<KtClassLikeSymbol> {
            if (position.getReceiverExpression() != null) return emptyList()

            val isAnnotationClassExpected = position.isNameReferenceInAnnotationEntry()

            val classesCandidates = sequence {
                yieldAll(indexProvider.getKotlinClassesByName(unresolvedName) { ktClassLikeDeclaration ->
                    ktClassLikeDeclaration.canBeImported() && (!isAnnotationClassExpected || ktClassLikeDeclaration.isAnnotation())
                })
                yieldAll(indexProvider.getJavaClassesByName(unresolvedName) { psiClass ->
                    psiClass.canBeImported() && (!isAnnotationClassExpected || psiClass.isAnnotationType)
                })
            }

            return classesCandidates
                .filter { isVisible(it) && it.classIdIfNonLocal != null }
                .toList()
        }

        private fun KtElement.isNameReferenceInAnnotationEntry(): Boolean =
            ((this as? KtNameReferenceExpression)?.parent?.parent?.parent as? KtConstructorCalleeExpression)?.parent is KtAnnotationEntry

        private fun KtClassLikeDeclaration.isAnnotation(): Boolean =
            this is KtClassOrObject && isAnnotation()
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
        is KtClassLikeDeclaration ->
            getClassId() != null && parentsOfType<KtClassLikeDeclaration>(withSelf = true).none { it.hasModifier(KtTokens.INNER_KEYWORD) }

        else -> false
    }
}
