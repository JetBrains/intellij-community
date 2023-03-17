// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.codereview.details.ReviewState
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindIcon
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JLabelUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gitlab.api.dto.GitLabCiJobDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestRemoveReviewerAction
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCiJobStatus
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsInfoViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel

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
      add(createCiStatusComponent(scope, detailsReviewFlowVm))
      add(createConflictsComponent(scope, detailsInfoVm))
      add(createNeedReviewerLabel(scope, detailsReviewFlowVm))
      add(createReviewersReviewStateLabel(scope, detailsReviewFlowVm, avatarIconsProvider))
    }
  }

  private fun createCiStatusComponent(scope: CoroutineScope, reviewDetailsVm: GitLabMergeRequestReviewFlowViewModel): JComponent {
    val ciJobs = reviewDetailsVm.pipeline.map { it?.jobs ?: emptyList() }
    val checkStatus = JLabel().apply {
      JLabelUtil.setTrimOverflow(this, true)
      bindIcon(scope, ciJobs.map { getCheckStatusIcon(it) })
      bindText(scope, ciJobs.map { getCheckStatusText(it) })
    }
    val detailsLink = ActionLink(CollaborationToolsBundle.message("review.details.status.ci.link.details")) {
      BrowserUtil.browse("${reviewDetailsVm.targetProject.value.webUrl}/-/jobs")
    }.apply {
      bindVisibility(scope, ciJobs.map { jobs ->
        jobs.any { it.status == GitLabCiJobStatus.PENDING || it.status == GitLabCiJobStatus.FAILED }
      })
    }

    return HorizontalListPanel(STATUS_COMPONENTS_GAP).apply {
      name = "CI statuses panel"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      bindVisibility(scope, ciJobs.map { it.isNotEmpty() })
      add(checkStatus)
      add(detailsLink)
    }
  }

  private fun createConflictsComponent(scope: CoroutineScope, detailsInfoVm: GitLabMergeRequestDetailsInfoViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Review conflicts label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.conflicts")
      JLabelUtil.setTrimOverflow(this, true)
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
      JLabelUtil.setTrimOverflow(this, true)
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
          panel.add(createReviewerReviewStatus(scope, detailsReviewFlowVm, reviewer, reviewState, avatarIconsProvider))
        }
        panel.revalidate()
        panel.repaint()
      }
    }

    return panel
  }

  private fun createReviewerReviewStatus(
    scope: CoroutineScope,
    detailsReviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
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
        text = ReviewDetailsUIUtil.getReviewStateText(reviewState, reviewer.name)
        JLabelUtil.setTrimOverflow(this, true)
        iconTextGap = STATUS_COMPONENTS_GAP
      }

      add(reviewStatusIconLabel)
      add(reviewerLabel)

      if (reviewState != ReviewState.ACCEPTED) {
        val actionGroup = DefaultActionGroup(GitLabMergeRequestRemoveReviewerAction(scope, detailsReviewFlowVm, reviewer).toAnAction())
        PopupHandler.installPopupMenu(this, actionGroup, "GitLabMergeRequestReviewerStatus")
      }
    }
  }

  private fun getCheckStatusIcon(ciJobs: List<GitLabCiJobDTO>): Icon? {
    val pendingJobs = ciJobs.count { it.status == GitLabCiJobStatus.PENDING }
    val failedJobs = ciJobs.count { it.status == GitLabCiJobStatus.FAILED }

    @Suppress("KotlinConstantConditions")
    return when {
      pendingJobs != 0 && failedJobs != 0 -> AllIcons.RunConfigurations.TestCustom
      pendingJobs == 0 && failedJobs == 0 -> AllIcons.RunConfigurations.TestPassed
      pendingJobs != 0 -> AllIcons.RunConfigurations.TestNotRan
      failedJobs != 0 -> AllIcons.RunConfigurations.TestError
      else -> null
    }
  }

  private fun getCheckStatusText(ciJobs: List<GitLabCiJobDTO>): String {
    val pendingJobs = ciJobs.count { it.status == GitLabCiJobStatus.PENDING }
    val failedJobs = ciJobs.count { it.status == GitLabCiJobStatus.FAILED }

    @Suppress("KotlinConstantConditions")
    return when {
      pendingJobs != 0 && failedJobs != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress.and.failed")
      pendingJobs == 0 && failedJobs == 0 -> CollaborationToolsBundle.message(
        "review.details.status.ci.passed"
      )
      pendingJobs != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress")
      failedJobs != 0 -> CollaborationToolsBundle.message("review.details.status.ci.failed")
      else -> ""
    }
  }
}