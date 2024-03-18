// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.name
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestSubmitReviewAction(
  parentCs: CoroutineScope,
  private val vm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(CollaborationToolsBundle.message("review.start.submit.action")) {
  private val cs = parentCs.childScope(Dispatchers.Main)

  init {
    cs.launchNow {
      combineAndCollect(
        vm.isBusy,
        vm.submittableReview
      ) { isBusy, review ->
        isEnabled = !isBusy && review != null
        val draftCommentsCount = review?.draftComments ?: 0
        name = if (draftCommentsCount <= 0) {
          CollaborationToolsBundle.message("review.start.submit.action")
        }
        else {
          CollaborationToolsBundle.message("review.start.submit.action.with.comments", draftCommentsCount)
        }
      }
    }
  }

  override fun actionPerformed(e: ActionEvent) {
    vm.submitReview()
  }
}