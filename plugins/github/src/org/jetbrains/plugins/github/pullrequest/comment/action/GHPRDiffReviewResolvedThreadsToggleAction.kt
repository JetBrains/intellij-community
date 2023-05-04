// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import org.jetbrains.plugins.github.pullrequest.comment.GHPRDiffReviewSupport

class GHPRDiffReviewResolvedThreadsToggleAction
  : ToggleAction({ CollaborationToolsBundle.message("review.diff.toolbar.show.resolved.threads") },
                 { CollaborationToolsBundle.message("review.diff.toolbar.show.resolved.threads.description") },
                 null) {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val reviewSupport = e.getData(GHPRDiffReviewSupport.DATA_KEY)
    e.presentation.isVisible = reviewSupport != null
    e.presentation.isEnabled = reviewSupport != null && reviewSupport.showReviewThreads
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    e.getData(GHPRDiffReviewSupport.DATA_KEY)?.showResolvedReviewThreads ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    e.getData(GHPRDiffReviewSupport.DATA_KEY)?.showResolvedReviewThreads = state
  }
}