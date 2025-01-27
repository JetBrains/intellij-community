// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt

import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.actions.KotlinAddImportActionInfo.executeListener
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinImportQuickFixAction
import org.jetbrains.kotlin.idea.quickfix.AutoImportVariant
import org.jetbrains.kotlin.idea.quickfix.ImportFixHelper
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class ImportLikeQuickFix(
    element: KtElement,
    protected val importVariants: List<AutoImportVariant>
) : KotlinImportQuickFixAction<KtElement>(element) {
    init {
        require(importVariants.isNotEmpty())
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (editor == null) return

        createImportAction(editor, file)?.execute()
    }

    override fun createImportAction(editor: Editor, file: KtFile): QuestionAction? =
        if (element != null) ImportQuestionAction(file.project, editor, file, importVariants) else null


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

    protected abstract fun fix(importVariant: AutoImportVariant, file: KtFile, project: Project)

    protected inner class ImportQuestionAction(
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
                    fix(importVariants.single(), file, project)
                    return true
                }

                0 -> {
                    return false
                }

                else -> {
                    if (onTheFly) return false

                    if (ApplicationManager.getApplication().isUnitTestMode) {
                        fix(importVariants.first(), file, project)
                        return true
                    }
                    ImportFixHelper.createListPopupWithImportVariants(project, importVariants) { variant ->
                        fix(variant, file, project)
                    }.showInBestPositionFor(editor)

                    return true
                }
            }
        }
    }
}