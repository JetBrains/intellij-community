// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.ide.DataManager
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.LocalQuickFixWithModCommandFallback
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandAction
import com.intellij.modcommand.ModStartRename
import com.intellij.modcommand.Presentation
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringFactory
import com.intellij.refactoring.rename.RenameHandlerRegistry
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle

open class RenameIdentifierFix : LocalQuickFixWithModCommandFallback {
    override fun getName(): String = KotlinBundle.message("rename.identifier.fix.text")
    override fun getFamilyName(): String = name

    override fun availableInBatchMode(): Boolean {
        return false
    }

    override fun startInWriteAction(): Boolean = false

    override fun getFallbackModCommandAction(): ModCommandAction = RenameIdentifierModCommandAction()

    private inner class RenameIdentifierModCommandAction : ModCommandAction {
        override fun getFamilyName(): String = this@RenameIdentifierFix.familyName

        override fun getPresentation(context: ActionContext): Presentation? {
            val element = context.element ?: return null

            return if (element.isValid && context.file.virtualFile != null) {
                Presentation.of(this@RenameIdentifierFix.name)
            } else {
                null
            }
        }

        override fun perform(context: ActionContext): ModCommand {
            val element = context.element ?: return ModCommand.nop()
            val virtualFile = context.file.virtualFile ?: return ModCommand.nop()
            val identifier = getFallbackNameIdentifier(element) ?: return ModCommand.nop()

            val range = ModStartRename.RenameSymbolRange(context.selection, identifier.textRange)

            return ModStartRename(virtualFile, range, /* nameSuggestions = */ emptyList())
        }

        override fun availableInBatchMode(): Boolean = false
    }

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val element = descriptor.psiElement ?: return
        val file = element.containingFile ?: return
        if (!FileModificationService.getInstance().prepareFileForWrite(file)) return
        val editorManager = FileEditorManager.getInstance(project)
        val fileEditor = editorManager.getSelectedEditor(file.virtualFile) ?: return renameWithoutEditor(element)
        val dataContext = DataManager.getInstance().getDataContext(fileEditor.component)
        val renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(dataContext)

        val editor = editorManager.selectedTextEditor
        if (editor != null) {
            renameHandler?.invoke(project, editor, file, dataContext)
        } else {
            val elementToRename = getElementToRename(element) ?: return
            renameHandler?.invoke(project, arrayOf(elementToRename), dataContext)
        }
    }

    protected open fun getFallbackNameIdentifier(element: PsiElement): PsiElement? = element

    protected open fun getElementToRename(element: PsiElement): PsiElement? = element.parent

    private fun renameWithoutEditor(element: PsiElement) {
        val elementToRename = getElementToRename(element) ?: return
        val factory = RefactoringFactory.getInstance(element.project)
        val renameRefactoring = factory.createRename(elementToRename, null, true, true)
        renameRefactoring.run()
    }
}