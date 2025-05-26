// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPRReviewDiffEditorModel

internal class GHPRDiffReviewNextCommentAction : AnAction(
  GithubBundle.messagePointer("pull.request.review.next.comment"),
  GithubBundle.messagePointer("pull.request.review.next.comment.description"),
  AllIcons.Actions.NextOccurence,
) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return

    val editor = e.getData(DiffDataKeys.CURRENT_EDITOR)

    val editorModel = editor?.getUserData(GHPRReviewDiffEditorModel.KEY)
    e.presentation.isVisible = editorModel != null
    if (editorModel == null) return

    val focused = findFocusedThreadId(project)
    e.presentation.isEnabled = if (focused != null) {
      editorModel.canGotoNextComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.canGotoNextComment(editorLine)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return

    val editor = e.getData(DiffDataKeys.CURRENT_EDITOR)
    val editorModel = editor?.getUserData(GHPRReviewDiffEditorModel.KEY)
    if (editorModel == null) return

    val focused = findFocusedThreadId(project)
    if (focused != null) {
      editorModel.gotoNextComment(focused)
    }
    else {
      val editorLine = editor.caretModel.logicalPosition.line // zero-index
      editorModel.gotoNextComment(editorLine)
    }
  }
}