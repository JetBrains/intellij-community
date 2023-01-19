// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.bindIcon
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestRequestedReviewer
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState.ChecksState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel.ReviewState
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GHPRStatusChecksComponentFactory {
  private const val STATUSES_GAP = 10
  private const val STATUS_COMPONENTS_GAP = 8
  private const val STATUS_COMPONENT_BORDER = 5
  private const val AVATAR_SIZE = 24

  fun create(
    parentScope: CoroutineScope,
    reviewDetailsVm: GHPRDetailsViewModel,
    reviewFlowVm: GHPRReviewFlowViewModel,
    securityService: GHPRSecurityService,
    avatarIconsProvider: GHAvatarIconsProvider
  ): JComponent {
    val scope = parentScope.childScope(Dispatchers.Main.immediate)
    val loadingPanel = createLoadingComponent(reviewDetailsVm, securityService)
    val checksPanel = JPanel(VerticalLayout(STATUSES_GAP)).apply {
      isOpaque = false
      add(createChecksStateComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createConflictsComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createRequiredReviewsComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createRestrictionsLabel(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createAccessDeniedLabel(reviewDetailsVm, securityService), VerticalLayout.TOP)
      add(createNeedReviewerLabel(scope, reviewFlowVm), VerticalLayout.TOP)
      add(createReviewersReviewStateLabel(scope, reviewFlowVm, avatarIconsProvider), VerticalLayout.TOP)
    }

    return Wrapper().apply {
      name = "Status check panel"
      bindContent(scope, reviewDetailsVm.mergeabilityState.map { mergeability ->
        if (mergeability == null) loadingPanel else checksPanel
      })
    }
  }

  private fun createLoadingComponent(reviewDetailsVm: GHPRDetailsViewModel, securityService: GHPRSecurityService): JComponent {
    val stateLabel = JLabel().apply {
      icon = AllIcons.RunConfigurations.TestNotRan
      text = GithubBundle.message("pull.request.loading.status")
    }
    val accessDeniedLabel = createAccessDeniedLabel(reviewDetailsVm, securityService)
    return JPanel(VerticalLayout(STATUSES_GAP)).apply {
      name = "Loading statuses panel"
      isOpaque = false
      add(stateLabel)
      add(accessDeniedLabel)
    }
  }

  private fun createChecksStateComponent(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    val checkStatus = JLabel().apply {
      bindIcon(scope, reviewDetailsVm.mergeabilityState.map { getCheckStatusIcon(it) })
      bindText(scope, reviewDetailsVm.mergeabilityState.map { getCheckStatusText(it) })
    }
    val detailsLink = ActionLink(CollaborationToolsBundle.message("review.details.status.ci.link.details")) {
      BrowserUtil.browse("${reviewDetailsVm.url}/checks")
    }.apply {
      bindVisibility(scope, reviewDetailsVm.mergeabilityState.map { mergeability ->
        mergeability != null && (mergeability.pendingChecks != 0 || mergeability.failedChecks != 0)
      })
    }

    return JPanel(HorizontalLayout(STATUS_COMPONENTS_GAP)).apply {
      isOpaque = false
      name = "CI statuses panel"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      bindVisibility(scope, reviewDetailsVm.checksState.map { it != ChecksState.NONE })
      add(checkStatus)
      add(detailsLink)
    }
  }

  private fun createConflictsComponent(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Review conflicts label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = GithubBundle.message("pull.request.conflicts.must.be.resolved")
      bindVisibility(scope, reviewDetailsVm.hasConflictsState.map { it == true })
    }
  }

  private fun createRequiredReviewsComponent(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Required reviews label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      bindVisibility(scope, combine(
        reviewDetailsVm.requiredApprovingReviewsCountState,
        reviewDetailsVm.isDraftState
      ) { requiredApprovingReviewsCount, isDraft ->
        requiredApprovingReviewsCount > 0 && !isDraft
      })
      bindText(scope, reviewDetailsVm.requiredApprovingReviewsCountState.map { requiredApprovingReviewsCount ->
        GithubBundle.message("pull.request.reviewers.required", requiredApprovingReviewsCount)
      })
    }
  }

  private fun createRestrictionsLabel(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Restricted rights label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = GithubBundle.message("pull.request.not.authorized.to.merge")
      bindVisibility(scope, combine(reviewDetailsVm.isRestrictedState, reviewDetailsVm.isDraftState) { isRestricted, isDraft ->
        isRestricted && !isDraft
      })
    }
  }

  private fun createAccessDeniedLabel(reviewDetailsVm: GHPRDetailsViewModel, securityService: GHPRSecurityService): JComponent {
    val isDraft = reviewDetailsVm.isDraftState.value
    val viewerDidAuthor = reviewDetailsVm.viewerDidAuthor

    val mergeForbidden = securityService.isMergeForbiddenForProject()
    val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
    val canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerDidAuthor
    val canMarkReadyForReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || viewerDidAuthor

    return JLabel().apply {
      isOpaque = false
      name = "Access denied label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      when {
        !canClose -> {
          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.repo.access.required")
        }
        !canMarkReadyForReview && isDraft -> {
          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.repo.write.access.required")
        }
        !canMerge && !isDraft -> {
          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.repo.write.access.required")
        }
        mergeForbidden && !isDraft -> {
          icon = AllIcons.RunConfigurations.TestError
          text = GithubBundle.message("pull.request.merge.disabled")
        }
        else -> {
          isVisible = false
        }
      }
    }
  }

  private fun createNeedReviewerLabel(scope: CoroutineScope, reviewFlowVm: GHPRReviewFlowViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Need reviewer label"
      border = JBUI.Borders.empty(STATUS_COMPONENT_BORDER, 0)
      icon = AllIcons.RunConfigurations.TestError
      text = CollaborationToolsBundle.message("review.details.status.reviewer.missing")
      bindVisibility(scope, reviewFlowVm.reviewerAndReviewState.map { it.isEmpty() })
    }
  }

  private fun createReviewersReviewStateLabel(
    scope: CoroutineScope,
    reviewFlowVm: GHPRReviewFlowViewModel,
    avatarIconsProvider: GHAvatarIconsProvider
  ): JComponent {
    val panel = JPanel(VerticalLayout(STATUSES_GAP)).apply {
      isOpaque = false
      name = "Reviewers statuses panel"
      border = JBUI.Borders.empty(1, 0)
      bindVisibility(scope, reviewFlowVm.reviewerAndReviewState.map { it.isNotEmpty() })
    }

    scope.launch {
      reviewFlowVm.reviewerAndReviewState.collect { reviewerAndReview ->
        panel.removeAll()
        reviewerAndReview.forEach { (reviewer, reviewState) ->
          panel.add(createReviewerReviewStatus(reviewer, reviewState, avatarIconsProvider), VerticalLayout.TOP)
        }
        panel.revalidate()
        panel.repaint()
      }
    }

    return panel
  }

  private fun createReviewerReviewStatus(
    reviewer: GHPullRequestRequestedReviewer,
    reviewState: ReviewState,
    avatarIconsProvider: GHAvatarIconsProvider
  ): JComponent {
    return JPanel(HorizontalLayout(STATUS_COMPONENTS_GAP)).apply {
      val reviewStatusIconLabel = JLabel().apply {
        icon = getReviewStateIcon(reviewState)
      }
      val reviewerLabel = JLabel().apply {
        icon = avatarIconsProvider.getIcon(reviewer.avatarUrl, AVATAR_SIZE)
        text = getReviewStateText(reviewState, reviewer.shortName)
        iconTextGap = STATUS_COMPONENTS_GAP
      }
      isOpaque = false
      add(reviewStatusIconLabel, HorizontalLayout.LEFT)
      add(reviewerLabel, HorizontalLayout.LEFT)
    }
  }

  private fun getReviewStateIcon(reviewState: ReviewState): Icon = when (reviewState) {
    ReviewState.ACCEPTED -> AllIcons.RunConfigurations.TestPassed
    ReviewState.WAIT_FOR_UPDATES -> AllIcons.RunConfigurations.TestError
    ReviewState.NEED_REVIEW -> AllIcons.RunConfigurations.TestFailed
  }

  private fun getReviewStateText(reviewState: ReviewState, reviewer: String): @Nls String = when (reviewState) {
    ReviewState.ACCEPTED -> CollaborationToolsBundle.message("review.details.status.reviewer.approved", reviewer)
    ReviewState.WAIT_FOR_UPDATES -> CollaborationToolsBundle.message("review.details.status.reviewer.wait.for.updates", reviewer)
    ReviewState.NEED_REVIEW -> CollaborationToolsBundle.message("review.details.status.reviewer.need.review", reviewer)
  }

  private fun getCheckStatusIcon(mergeability: GHPRMergeabilityState?): Icon? {
    mergeability ?: return null
    @Suppress("KotlinConstantConditions")
    return when {
      mergeability.pendingChecks != 0 && mergeability.failedChecks != 0 -> AllIcons.RunConfigurations.TestCustom
      mergeability.pendingChecks == 0 && mergeability.failedChecks == 0 -> AllIcons.RunConfigurations.TestPassed
      mergeability.pendingChecks != 0 -> AllIcons.RunConfigurations.TestNotRan
      mergeability.failedChecks != 0 -> AllIcons.RunConfigurations.TestError
      else -> null
    }
  }

  private fun getCheckStatusText(mergeability: GHPRMergeabilityState?): String {
    mergeability ?: return ""
    @Suppress("KotlinConstantConditions")
    return when {
      mergeability.pendingChecks != 0 && mergeability.failedChecks != 0 -> CollaborationToolsBundle.message(
        "review.details.status.ci.progress.and.failed"
      )
      mergeability.pendingChecks == 0 && mergeability.failedChecks == 0 -> CollaborationToolsBundle.message(
        "review.details.status.ci.passed"
      )
      mergeability.pendingChecks != 0 -> CollaborationToolsBundle.message("review.details.status.ci.progress")
      mergeability.failedChecks != 0 -> CollaborationToolsBundle.message("review.details.status.ci.failed")
      else -> ""
    }
  }
}