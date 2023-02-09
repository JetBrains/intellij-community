// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.codereview.details.ReviewState
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import javax.swing.JComponent
import javax.swing.JLabel

// TODO: implement another statuses
internal object GitLabMergeRequestDetailsStatusChecksComponentFactory {
  private const val STATUSES_GAP = 10
  private const val STATUS_COMPONENTS_GAP = 8
  private const val STATUS_COMPONENT_BORDER = 5
  private const val AVATAR_SIZE = 24

  fun create(
    scope: CoroutineScope,
    detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel,
    detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    return VerticalListPanel(STATUSES_GAP).apply {
      add(createConflictsComponent(scope, detailsInfoVm))
      add(createNeedReviewerLabel(scope, detailsReviewFlowVm))
      add(createReviewersReviewStateLabel(scope, detailsReviewFlowVm, avatarIconsProvider))
    }
  }

  private fun createConflictsComponent(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Review conflicts label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.conflicts")
      bindVisibility(scope, detailsInfoVm.hasConflicts)
    }
  }

  private fun createNeedReviewerLabel(scope: CoroutineScope, detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Need reviewer label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.reviewer.missing")
      bindVisibility(scope, detailsReviewFlowVm.reviewerAndReviewState.map { it.isEmpty() })
    }
  }

  private fun createReviewersReviewStateLabel(
    scope: CoroutineScope,
    detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val panel = VerticalListPanel(STATUSES_GAP).apply {
      name = "Reviewers statuses panel"
      border = JBUI.Borders.empty(1, 0)
      bindVisibility(scope, detailsReviewFlowVm.reviewerAndReviewState.map { it.isNotEmpty() })
    }

    scope.launch {
      detailsReviewFlowVm.reviewerAndReviewState.collect { reviewerAndReview ->
        panel.removeAll()
        reviewerAndReview.forEach { (reviewer, reviewState) ->
          panel.add(createReviewerReviewStatus(reviewer, reviewState, avatarIconsProvider))
        }
        panel.revalidate()
        panel.repaint()
      }
    }

    return panel
  }

  private fun createReviewerReviewStatus(
    reviewer: GitLabUserDTO,
    reviewState: ReviewState,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    return HorizontalListPanel(STATUS_COMPONENTS_GAP).apply {
      val reviewStatusIconLabel = JLabel().apply {
        icon = ReviewDetailsUIUtil.getReviewStateIcon(reviewState)
      }
      val reviewerLabel = JLabel().apply {
        icon = avatarIconsProvider.getIcon(reviewer, AVATAR_SIZE)
        text = ReviewDetailsUIUtil.getReviewStateText(reviewState, reviewer.username)
        iconTextGap = STATUS_COMPONENTS_GAP
      }

      add(reviewStatusIconLabel)
      add(reviewerLabel)
    }
  }
}