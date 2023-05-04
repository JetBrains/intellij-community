// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

class GHPRDiffReviewThreadsToggleAction
  : ToggleAction({ CollaborationToolsBundle.message("review.diff.toolbar.show.comments.action") },
                 { CollaborationToolsBundle.message("review.diff.toolbar.show.comments.action.description") },
                 null) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = e.getData(GHPRDiffReviewSupport.DATA_KEY) != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    e.getData(GHPRDiffReviewSupport.DATA_KEY)?.showReviewThreads ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(GHPRDiffReviewSupport.DATA_KEY)?.showReviewThreads = state
  }
}