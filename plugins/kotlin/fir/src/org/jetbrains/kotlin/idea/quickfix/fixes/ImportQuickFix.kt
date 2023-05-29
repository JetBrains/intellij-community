// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix.fixes

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.ShowAutoImportPass
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInspection.HintAction
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.*
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parentsOfType
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.fir.utils.addImportToFile
import org.jetbrains.kotlin.analysis.api.symbols.KtCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtNamedClassOrObjectSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KtSymbol
import org.jetbrains.kotlin.analysis.api.symbols.markers.KtSymbolWithVisibility
import org.jetbrains.kotlin.idea.base.analysis.api.utils.KtSymbolFromIndexProvider
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.diagnosticFixFactory
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.QuickFixActionBase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtPsiUtil.isSelectorInQualified
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.unwrapNullability

class ImportQuickFix(
    element: KtElement,
    private val importCandidates: List<FqName>
) : QuickFixActionBase<KtElement>(element), HintAction {

    init {
        require(importCandidates.isNotEmpty())
    }

    override fun getText(): String = KotlinBundle.message("fix.import")

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        if (file !is KtFile) return

        createAddImportAction(project, editor, file).execute()
    }

    private fun createAddImportAction(project: Project, editor: Editor, file: KtFile): QuestionAction {
        return ImportQuestionAction(project, editor, file, importCandidates)
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
        val autoImportHintText = KotlinBundle.message("fix.import.question", importCandidates.first().asString())

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
        if (importCandidates.size == 1) {
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
        private val importCandidates: List<FqName>
    ) : QuestionAction {

        init {
            require(importCandidates.isNotEmpty())
        }

        override fun execute(): Boolean {
            when (importCandidates.size) {
                1 -> {
                    addImport(importCandidates.single())
                    return true
                }

                0 -> {
                    return false
                }

                else -> {
                    if (ApplicationManager.getApplication().isUnitTestMode) return false
                    createImportSelectorPopup().showInBestPositionFor(editor)

                    return true
                }
            }
        }

        private fun createImportSelectorPopup(): JBPopup {
            return JBPopupFactory.getInstance()
                .createPopupChooserBuilder(importCandidates)
                .setTitle(KotlinBundle.message("action.add.import.chooser.title"))
                .setItemChosenCallback { selectedValue: FqName -> addImport(selectedValue) }
                .createPopup()
        }

        private fun addImport(nameToImport: FqName) {
            project.executeWriteCommand(QuickFixBundle.message("add.import")) {
                addImportToFile(project, file, nameToImport)
            }
        }
    }

    companion object {
        val FACTORY = diagnosticFixFactory(KtFirDiagnostic.UnresolvedReference::class) { diagnostic ->
            val element = diagnostic.psi

            val project = element.project
            val indexProvider = KtSymbolFromIndexProvider(project)

            val quickFix = when (element) {
                is KtTypeReference -> createImportTypeFix(indexProvider, element)
                is KtNameReferenceExpression -> {
                    if (isSelectorInQualified(element)) null
                    else createImportNameFix(indexProvider, element, element.getReferencedNameAsName())
                }
                else -> null
            }

            listOfNotNull(quickFix)
        }

        fun KtAnalysisSession.createImportNameFix(
            indexProvider: KtSymbolFromIndexProvider,
            element: KtReferenceExpression,
            unresolvedName: Name
        ): ImportQuickFix? {
            val firFile = element.containingKtFile.getFileSymbol()

            val isVisible: (KtSymbol) -> Boolean =
                { it !is KtSymbolWithVisibility || isVisible(it, firFile, null, element) }

            val callableCandidates = collectCallableCandidates(indexProvider, unresolvedName, isVisible)
            val typeCandidates = collectTypesCandidates(indexProvider, unresolvedName, isVisible)

            val importCandidates = (callableCandidates + typeCandidates).distinct()
            if (importCandidates.isEmpty()) return null

            return ImportQuickFix(element, importCandidates)
        }

        private fun KtAnalysisSession.createImportTypeFix(
            indexProvider: KtSymbolFromIndexProvider,
            element: KtTypeReference
        ): ImportQuickFix? {
            val firFile = element.containingKtFile.getFileSymbol()
            val unresolvedName = element.typeName ?: return null

            val isVisible: (KtNamedClassOrObjectSymbol) -> Boolean =
                { isVisible(it, firFile, null, element) }

            val acceptableClasses = collectTypesCandidates(indexProvider, unresolvedName, isVisible).distinct()
            if (acceptableClasses.isEmpty()) return null

            return ImportQuickFix(element, acceptableClasses)
        }

        private fun KtAnalysisSession.collectCallableCandidates(
            indexProvider: KtSymbolFromIndexProvider,
            unresolvedName: Name,
            isVisible: (KtCallableSymbol) -> Boolean
        ): List<FqName> {
            val callablesCandidates =
                indexProvider.getKotlinCallableSymbolsByName(unresolvedName) { it.canBeImported() } +
                indexProvider.getJavaCallableSymbolsByName(unresolvedName) { it.canBeImported() }

            return callablesCandidates
                .filter(isVisible)
                .mapNotNull { it.callableIdIfNonLocal?.asSingleFqName() }
                .toList()
        }

        private fun KtAnalysisSession.collectTypesCandidates(
            indexProvider: KtSymbolFromIndexProvider,
            unresolvedName: Name,
            isVisible: (KtNamedClassOrObjectSymbol) -> Boolean
        ): List<FqName> {
            val classesCandidates =
                indexProvider.getKotlinClassesByName(unresolvedName) { it.canBeImported() } +
                        indexProvider.getJavaClassesByName(unresolvedName) { it.canBeImported() }

            return classesCandidates
                .filter(isVisible)
                .mapNotNull { it.classIdIfNonLocal?.asSingleFqName() }
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