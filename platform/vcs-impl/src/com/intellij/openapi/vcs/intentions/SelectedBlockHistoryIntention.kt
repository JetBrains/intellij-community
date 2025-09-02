// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.intentions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.actions.SelectedBlockHistoryAction
import com.intellij.psi.PsiFile
import com.intellij.vcsUtil.VcsSelection

// The intention doesn't change the code, so it can't have `.before` and `.after` templates.
@Suppress("IntentionDescriptionNotFoundInspection")
class SelectedBlockHistoryIntention : IntentionAction, LowPriorityAction {

  override fun startInWriteAction(): Boolean = false

  override fun getFamilyName(): String = CodeInsightBundle.message("intention.show.history.for.block.text")

  override fun getText(): String = familyName

  override fun isAvailable(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
    val selection = getSelection(editor) ?: return false
    return SelectedBlockHistoryAction.isEnabled(project, selection)
  }

  override fun invoke(project: Project, editor: Editor, psiFile: PsiFile) {
    val selection = getSelection(editor) ?: return
    SelectedBlockHistoryAction.showHistoryForSelection(selection, project)
  }

  private fun getSelection(editor: Editor): VcsSelection? {
    val selectionModel = editor.selectionModel
    if (selectionModel.hasSelection()) {
      return VcsSelection(editor.document,
                          TextRange(selectionModel.selectionStart, selectionModel.selectionEnd),
                          VcsBundle.message("action.name.show.history.for.selection"))
    }
    return null
  }
}