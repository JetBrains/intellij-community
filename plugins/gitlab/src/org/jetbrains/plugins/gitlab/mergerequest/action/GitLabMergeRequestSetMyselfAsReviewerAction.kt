// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestSetMyselfAsReviewerAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.set.myself.as.reviewer")) {
  init {
    scope.launch {
      reviewFlowVm.isBusy.collect { isBusy ->
        isEnabled = !isBusy
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) = reviewFlowVm.setMyselfAsReviewer()
}