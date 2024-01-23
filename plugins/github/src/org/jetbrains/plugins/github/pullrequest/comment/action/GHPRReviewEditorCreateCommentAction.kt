// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.diff.util.LineRange
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.util.asSafely
import org.jetbrains.plugins.github.pullrequest.ui.diff.GHPREditorReviewModel

internal class GHPRReviewEditorCreateCommentAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isEnabledAndVisible(e)
  }

  private fun isEnabledAndVisible(e: AnActionEvent): Boolean {
    val editor = e.getData(CommonDataKeys.EDITOR).asSafely<EditorEx>() ?: return false
    val model = editor.getUserData(GHPREditorReviewModel.KEY) ?: return false
    val selectedRange = editor.getSelectedLinesRange()
    if (selectedRange != null) {
      return model.canCreateComment(selectedRange)
    }

    val caretLine = editor.caretModel.logicalPosition.line.takeIf { it >= 0 }
    return caretLine != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getRequiredData(CommonDataKeys.EDITOR).asSafely<EditorEx>() ?: return
    val model = editor.getUserData(GHPREditorReviewModel.KEY) ?: return
    val scrollingModel = editor.scrollingModel

    val selectedRange = editor.getSelectedLinesRange()
    if (selectedRange != null) {
      scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
      scrollingModel.runActionOnScrollingFinished {
        model.requestNewComment(selectedRange)
      }
      return
    }

    val caretLine = editor.caretModel.logicalPosition.line.takeIf { it >= 0 } ?: return
    scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    scrollingModel.runActionOnScrollingFinished {
      model.requestNewComment(caretLine)
    }
  }

  private fun EditorEx.getSelectedLinesRange(): LineRange? {
    if (!selectionModel.hasSelection()) return null
    return with(selectionModel) {
      LineRange(editor.offsetToLogicalPosition(selectionStart).line, editor.offsetToLogicalPosition(selectionEnd).line)
    }
  }
}