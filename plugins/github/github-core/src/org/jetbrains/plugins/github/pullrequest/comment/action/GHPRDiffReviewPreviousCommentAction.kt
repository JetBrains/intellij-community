// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRReviewDiffEditorModel

internal class GHPRDiffReviewPreviousCommentAction : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return

    val editor = e.getData(DiffDataKeys.CURRENT_EDITOR) ?: e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)

    val editorModel = editor?.getUserData(CodeReviewNavigableEditorViewModel.KEY)
                      ?: editor?.getUserData(GHPRReviewDiffEditorModel.KEY)
    e.presentation.isVisible = editorModel != null
    if (editor == null || editorModel == null) return

    val focused = findFocusedThreadId(project)
    e.presentation.isEnabled = if (focused != null) {
      editorModel.canGotoPreviousComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.canGotoPreviousComment(editorLine)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val editor = e.getData(DiffDataKeys.CURRENT_EDITOR) ?: e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
    val editorModel = editor?.getUserData(CodeReviewNavigableEditorViewModel.KEY)
                      ?: editor?.getUserData(GHPRReviewDiffEditorModel.KEY)
    if (editor == null || editorModel == null) return

    val focused = findFocusedThreadId(project)
    if (focused != null) {
      editorModel.gotoPreviousComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.gotoPreviousComment(editorLine)
    }
  }
}