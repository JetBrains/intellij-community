// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.ui.codereview.editor.CodeReviewNavigableEditorViewModel
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRReviewDiffEditorModel

internal class GHPRDiffReviewPreviousCommentAction : GHPRDiffReviewPreviousNextCommentActionBase(
  canGotoThreadComment = { canGotoPreviousComment(it) },
  canGotoLineComment = { canGotoPreviousComment(it) },
  gotoThreadComment = { gotoPreviousComment(it) },
  gotoLineComment = { gotoPreviousComment(it) },
)

internal class GHPRDiffReviewNextCommentAction : GHPRDiffReviewPreviousNextCommentActionBase(
  canGotoThreadComment = { canGotoNextComment(it) },
  canGotoLineComment = { canGotoNextComment(it) },
  gotoThreadComment = { gotoNextComment(it) },
  gotoLineComment = { gotoNextComment(it) },
)

internal sealed class GHPRDiffReviewPreviousNextCommentActionBase(
  private val canGotoThreadComment: CodeReviewNavigableEditorViewModel.(String) -> Boolean,
  private val canGotoLineComment: CodeReviewNavigableEditorViewModel.(Int) -> Boolean,
  private val gotoThreadComment: CodeReviewNavigableEditorViewModel.(String) -> Unit,
  private val gotoLineComment: CodeReviewNavigableEditorViewModel.(Int) -> Unit,
) : AnAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return

    val editor = e.getData(DiffDataKeys.CURRENT_EDITOR) ?: e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)

    val editorModel = editor?.getUserData(CodeReviewNavigableEditorViewModel.KEY)
                      ?: editor?.getUserData(GHPRReviewDiffEditorModel.KEY)
    if (editor == null || editorModel == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    val focused = findFocusedThreadId(project)
    e.presentation.isEnabled = if (focused != null) {
      editorModel.canGotoThreadComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.canGotoLineComment(editorLine)
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
      editorModel.gotoThreadComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.gotoLineComment(editorLine)
    }
  }
}
