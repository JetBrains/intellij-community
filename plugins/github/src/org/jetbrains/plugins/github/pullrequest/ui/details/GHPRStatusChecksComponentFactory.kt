// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.bindText
import com.intellij.collaboration.ui.util.bindVisibility
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState.ChecksState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRDetailsViewModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal object GHPRStatusChecksComponentFactory {
  private const val STATUSES_GAP = 10
  private const val STATUS_COMPONENTS_GAP = 8

  fun create(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel, securityService: GHPRSecurityService): JComponent {
    val loadingPanel = createLoadingComponent(reviewDetailsVm, securityService)
    val checksPanel = JPanel(VerticalLayout(STATUSES_GAP)).apply {
      isOpaque = false
      add(createChecksStateComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createConflictsComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createRequiredReviewsComponent(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createRestrictionsLabel(scope, reviewDetailsVm), VerticalLayout.TOP)
      add(createAccessDeniedLabel(reviewDetailsVm, securityService), VerticalLayout.TOP)
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
    val checksPassed = JLabel().apply {
      name = "Passed CI label"
      icon = AllIcons.RunConfigurations.TestPassed
      text = CollaborationToolsBundle.message("review.details.status.ci.passed")
      bindVisibility(scope, reviewDetailsVm.mergeabilityState.map { it != null && it.failedChecks == 0 && it.pendingChecks == 0 })
    }

    val checksPending = JPanel(HorizontalLayout(STATUS_COMPONENTS_GAP)).apply {
      val checksPendingLabel = JLabel().apply {
        icon = AllIcons.RunConfigurations.TestNotRan
        text = CollaborationToolsBundle.message("review.details.status.ci.progress")
      }

      isOpaque = false
      name = "Pending CI label"
      add(checksPendingLabel, HorizontalLayout.LEFT)
      add(createDetailsLink("${reviewDetailsVm.urlState.value}/checks"), HorizontalLayout.LEFT)
      bindVisibility(scope, reviewDetailsVm.mergeabilityState.map { it != null && it.pendingChecks != 0 })
    }

    val checksFailed = JPanel(HorizontalLayout(STATUS_COMPONENTS_GAP)).apply {
      val checksFailedLabel = JLabel().apply {
        icon = AllIcons.RunConfigurations.TestNotRan
        text = CollaborationToolsBundle.message("review.details.status.ci.failed")
      }

      isOpaque = false
      name = "Failed CI label"
      add(checksFailedLabel, HorizontalLayout.LEFT)
      add(createDetailsLink("${reviewDetailsVm.urlState.value}/checks"), HorizontalLayout.LEFT)
      bindVisibility(scope, reviewDetailsVm.mergeabilityState.map { it != null && it.failedChecks != 0 })
    }

    return JPanel(HorizontalLayout(STATUS_COMPONENTS_GAP)).apply {
      isOpaque = false
      name = "CI statuses panel"
      bindVisibility(scope, reviewDetailsVm.checksState.map { it != ChecksState.NONE })

      add(checksPassed)
      add(checksPending)
      add(checksFailed)
    }
  }

  private fun createConflictsComponent(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Review conflicts label"
      icon = AllIcons.RunConfigurations.TestError
      text = GithubBundle.message("pull.request.conflicts.must.be.resolved")
      bindVisibility(scope, reviewDetailsVm.hasConflictsState.map { it == true })
    }
  }

  private fun createRequiredReviewsComponent(scope: CoroutineScope, reviewDetailsVm: GHPRDetailsViewModel): JComponent {
    return JLabel().apply {
      isOpaque = false
      name = "Required reviews label"
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
      name = "Restricted rights label"
      icon = AllIcons.RunConfigurations.TestError
      text = GithubBundle.message("pull.request.not.authorized.to.merge")
      bindVisibility(scope, combine(reviewDetailsVm.isRestrictedState, reviewDetailsVm.isDraftState) { isRestricted, isDraft ->
        isRestricted && !isDraft
      })
    }
  }

  private fun createAccessDeniedLabel(reviewDetailsVm: GHPRDetailsViewModel, securityService: GHPRSecurityService): JComponent {
    val isDraft = reviewDetailsVm.isDraftState.value
    val viewerDidAuthor = reviewDetailsVm.viewerDidAuthorState

    val mergeForbidden = securityService.isMergeForbiddenForProject()
    val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
    val canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerDidAuthor
    val canMarkReadyForReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || viewerDidAuthor

    return JLabel().apply {
      name = "Access denied label"
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

  private fun createDetailsLink(url: String): JComponent {
    return ActionLink(CollaborationToolsBundle.message("review.details.status.ci.link.details")) {
      BrowserUtil.browse(url)
    }
  }
}