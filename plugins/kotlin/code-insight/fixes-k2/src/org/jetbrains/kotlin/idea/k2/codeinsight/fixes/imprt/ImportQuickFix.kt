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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.psi.imports.addImport
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
class ImportQuickFix(
    element: KtElement,
    @IntentionName private val text: String,
    importVariants: List<AutoImportVariant>
) : ImportLikeQuickFix(element, importVariants), HintAction, HighPriorityAction {
    override fun getText(): String = text

    override fun getFamilyName(): String = KotlinBundle.message("fix.import")

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


    override fun fix(importVariant: AutoImportVariant, file: KtFile, project: Project) {
        require(importVariant is SymbolBasedAutoImportVariant)

        StatisticsManager.getInstance().incUseCount(importVariant.statisticsInfo)

        project.executeWriteCommand(QuickFixBundle.message("add.import")) {
            file.addImport(importVariant.fqName)
        }
    }
}