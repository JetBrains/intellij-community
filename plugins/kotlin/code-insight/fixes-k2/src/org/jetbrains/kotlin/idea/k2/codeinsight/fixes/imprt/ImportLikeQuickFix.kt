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
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile

@ApiStatus.Internal
abstract class ImportLikeQuickFix(
    element: KtElement,
    protected val importVariants: List<AutoImportVariant>
) : KotlinImportQuickFixAction<KtElement>(element) {
    private val modificationCountOnCreate: Long = PsiModificationTracker.getInstance(element.project).modificationCount

    init {
        require(importVariants.isNotEmpty())
    }

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        if (editor == null) return

        createImportAction(editor, file)?.execute()
    }

    override fun createImportAction(editor: Editor, file: KtFile): QuestionAction? =
        if (element != null) ImportQuestionAction(file.project, editor, file, importVariants) else null

    override fun isAvailable(project: Project, editor: Editor?, file: KtFile): Boolean {
        if (modificationCountOnCreate == PsiModificationTracker.getInstance(project).modificationCount) {
            // optimization: we know nothing was changed since the last isAvailable() call
            return true
        }
        val fqName = importVariants.firstOrNull()?.fqName
        return fqName == null || !isClassDefinitelyPositivelyImportedAlready(file, fqName)
    }

    /**
     * @return true if the class candidate name to be imported already present in the import list (maybe some auto-import-fix for another reference did it?)
     * This method is intended to be cheap and resolve-free, because it might be called in EDT.
     * This method is used as an optimization against trying to import the same class several times,
     * so false negatives are fine (returning false even when the class already imported is OK) whereas false positives are bad (don't return true when the class wasn't imported).
     */
    protected open fun isClassDefinitelyPositivelyImportedAlready(containingFile: KtFile, classQualifiedName: FqName): Boolean {
        return false
    }

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