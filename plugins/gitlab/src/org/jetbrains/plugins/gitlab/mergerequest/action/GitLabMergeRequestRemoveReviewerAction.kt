// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.action

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import java.awt.event.ActionEvent
import javax.swing.AbstractAction

internal class GitLabMergeRequestRemoveReviewerAction(
  private val detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
  private val reviewer: GitLabUserDTO
) : AbstractAction(CollaborationToolsBundle.message("review.details.action.remove.reviewer", reviewer.username)) {
  override fun actionPerformed(event: ActionEvent) = detailsReviewFlowVm.removeReviewer(reviewer)
}