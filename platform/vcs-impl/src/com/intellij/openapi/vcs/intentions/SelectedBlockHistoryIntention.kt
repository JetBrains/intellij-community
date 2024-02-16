// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.intentions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.actions.SelectedBlockHistoryAction
import com.intellij.psi.PsiFile
import com.intellij.vcsUtil.VcsSelectionUtil

class SelectedBlockHistoryIntention : IntentionAction, LowPriorityAction {

  override fun startInWriteAction(): Boolean = false

  override fun getFamilyName(): String = CodeInsightBundle.message("intention.show.history.for.block.text")

  override fun getText(): String = familyName

  override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
    val selection = VcsSelectionUtil.getSelection(editor) ?: return false
    return SelectedBlockHistoryAction.isEnabled(project, selection)
  }

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val selection = VcsSelectionUtil.getSelection(editor) ?: return
    SelectedBlockHistoryAction.showHistoryForSelection(selection, project)
  }
}