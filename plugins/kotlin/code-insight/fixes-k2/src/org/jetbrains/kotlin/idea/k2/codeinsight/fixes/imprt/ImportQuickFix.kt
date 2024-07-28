// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.kotlin.idea.actions.KotlinAddImportActionInfo.executeListener
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    private val importVariants: List<AutoImportVariant>
) : KotlinImportQuickFixAction<KtElement>(element), HintAction, HighPriorityAction {
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
}