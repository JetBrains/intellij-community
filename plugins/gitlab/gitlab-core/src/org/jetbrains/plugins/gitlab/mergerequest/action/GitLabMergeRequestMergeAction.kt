// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.async.combineAndCollect
import com.intellij.collaboration.messages.CollaborationToolsBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.GitLabMergeRequestMergeDialog
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestMergeAction(
  scope: CoroutineScope,
  private val reviewFlowVm: GitLabMergeRequestReviewFlowViewModel
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.merge")) {
  init {
    scope.launch {
      combineAndCollect(reviewFlowVm.isBusy, reviewFlowVm.mergeDetails) { isBusy, mergeDetails ->
        isEnabled = !isBusy && mergeDetails?.canMerge ?: false
      }
    }
  }

  override fun actionPerformed(e: ActionEvent?) {
    val details = reviewFlowVm.mergeDetails.value ?: return
    val dialog = GitLabMergeRequestMergeDialog(
      project = reviewFlowVm.project,
      mergeRequestDisplayName = reviewFlowVm.number,
      sourceBranchName = details.sourceBranch,
      targetBranchName = details.targetBranch,
      needMergeCommit = !details.ffOnlyMerge,
      mergeCommitMessageDefault = details.mergeCommitMessageDefault,
      removeSourceBranchDefault = details.removeSourceBranch,
      squashCommitsDefault = details.squashCommits,
      squashCommitsReadonly = details.squashCommitsReadonly,
      squashCommitMessageDefault = details.squashCommitMessageDefault
    )
    if (!dialog.showAndGet()) return
    val data = dialog.getData()
    if (data.squashCommits) {
      reviewFlowVm.squashAndMerge(
        mergeCommitMessage = data.mergeCommitMessage,
        removeSourceBranch = data.removeSourceBranch,
        squashCommitMessage = data.squashCommitMessage
      )
    }
    else {
      reviewFlowVm.merge(
        mergeCommitMessage = data.mergeCommitMessage,
        removeSourceBranch = data.removeSourceBranch
      )
    }
  }
}