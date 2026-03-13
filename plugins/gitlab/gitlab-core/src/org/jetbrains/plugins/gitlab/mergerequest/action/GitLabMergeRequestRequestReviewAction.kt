// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.list.search.ShowDirection
import com.intellij.openapi.application.UI
import com.intellij.platform.util.coroutines.sync.OverflowSemaphore
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestChoosersUtil
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.JComponent

internal class GitLabMergeRequestRequestReviewAction(
  private val scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.request")) {
  private val sem = OverflowSemaphore(1, BufferOverflow.DROP_OLDEST)

  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.userCanManage) { isBusy, userCanManageReview ->
        isEnabled = !isBusy && userCanManageReview
      }
    }
  }

  override fun actionPerformed(event: ActionEvent) {
    val parentComponent = event.source as? JComponent ?: return
    val point = RelativePoint.getSouthWestOf(parentComponent)
    scope.launch(Dispatchers.UI) {
      val allowsMultipleReviewers = reviewFlowVm.allowsMultipleReviewers.first()
      val currentReviewers = reviewFlowVm.reviewers.value
      val updatedReviewers = sem.withPermit {
        if (allowsMultipleReviewers) {
          GitLabMergeRequestChoosersUtil.chooseUsers(
            point,
            currentReviewers,
            reviewFlowVm.projectMembers,
            reviewFlowVm.avatarIconsProvider,
            ShowDirection.ABOVE
          )
        }
        else {
          GitLabMergeRequestChoosersUtil.chooseUser(
            point,
            reviewFlowVm.projectMembers,
            reviewFlowVm.avatarIconsProvider,
            ShowDirection.ABOVE
          )?.let { listOfNotNull(it) }
        }
      }
      updatedReviewers ?: return@launch
      reviewFlowVm.setReviewers(updatedReviewers)
    }
  }
}
