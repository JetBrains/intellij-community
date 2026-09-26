// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.backend.completion.commands

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.command.CommandCompletionProviderContext
import com.intellij.codeInsight.completion.command.commands.ActionCommandProvider
import com.intellij.codeInsight.completion.command.commands.ActionCompletionCommand
import com.intellij.codeInsight.multiverse.EditorContextManager
import com.intellij.ide.structureView.impl.common.PsiTreeElementBase
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.platform.structureView.backend.showFileStructurePopup
import com.intellij.platform.structureView.impl.actions.ViewStructureActionBase
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.util.PsiTreeUtil

class ViewStructureCompletionCommandProvider : ActionCommandProvider(actionId = "FileStructurePopup", synonyms = listOf("File Structure", "Go to members", "Show structure"), presentableName = CodeInsightBundle.message("command.completion.view.structure.text"), priority = -150, previewText = ActionsBundle.message("action.FileStructurePopup.description")) {

  override fun supportNewLineCompletion(): Boolean = true

  override fun isApplicable(offset: Int, psiFile: PsiFile, editor: Editor?): Boolean {
    if (editor == null) return false
    val fileEditor = TextEditorProvider.getInstance().getTextEditor(editor)
    return ViewStructureActionBase.isPopupAvailableFor(fileEditor, editor) && fileEditor.structureViewBuilder != null
  }

  override fun createCommand(context: CommandCompletionProviderContext): ActionCompletionCommand {
    val callback = callback@{ node: AbstractTreeNode<*> ->
      val psiTreeElementBase = node.getValue() as? PsiTreeElementBase<*> ?: return@callback
      val psiElement = psiTreeElementBase.element ?: return@callback
      if (!psiElement.isPhysical) return@callback
      if (!psiElement.isValid) return@callback
      if (psiElement !is PsiNameIdentifierOwner) return@callback
      val endOffset = psiElement.nameIdentifier?.textRange?.endOffset ?: return@callback
      val project = context.project
      val selectedTextEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return@callback
      val psiFile = EditorContextManager.getPsiFileForEditor(selectedTextEditor, project) ?: return@callback
      if (!PsiTreeUtil.isAncestor(psiFile, psiElement, false)) return@callback
      selectedTextEditor.caretModel.moveToOffset(endOffset)
    }
    return object : ActionCompletionCommand(actionId = super.actionId, presentableActionName = super.presentableName, icon = super.icon, priority = super.priority, previewText = super.previewText, synonyms = super.synonyms) {
      override val action: AnAction = object : ViewStructureActionBase() {
        override fun showFileStructurePopup(project: Project, fileEditor: FileEditor) {
          showFileStructurePopup(project, fileEditor, fileEditor.file, null, callback)
        }
      }
    }
  }
}
