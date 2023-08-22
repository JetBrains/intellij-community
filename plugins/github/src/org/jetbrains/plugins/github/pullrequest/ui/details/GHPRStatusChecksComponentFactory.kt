// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsStatusComponentFactory
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.icons.ExpUiIcons
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.childScope
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRRemoveReviewerAction
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JScrollPane

internal object GHPRStatusChecksComponentFactory {
  fun create(
    parentScope: CoroutineScope,
    project: Project,
    reviewStatusVm: GHPRStatusViewModel,
    reviewFlowVm: GHPRReviewFlowViewModel,
    securityService: GHPRSecurityService,
    avatarIconsProvider: GHAvatarIconsProvider
  ): JComponent {
    val scope = parentScope.childScope(Dispatchers.Main.immediate)
    val loadingPanel = createLoadingComponent(scope, reviewStatusVm, securityService)
    val statusesPanel = VerticalListPanel().apply {
      add(createAccessDeniedLabel(scope, reviewStatusVm, securityService))
      add(CodeReviewDetailsStatusComponentFactory.createCiComponent(scope, reviewStatusVm))
      add(CodeReviewDetailsStatusComponentFactory.createConflictsComponent(scope, reviewStatusVm.hasConflicts))
      add(CodeReviewDetailsStatusComponentFactory.createRequiredReviewsComponent(scope,
                                                                                 reviewStatusVm.requiredApprovingReviewsCount,
                                                                                 reviewStatusVm.isDraft))
      add(CodeReviewDetailsStatusComponentFactory.createRestrictionComponent(scope,
                                                                             reviewStatusVm.isRestricted,
                                                                             reviewStatusVm.isDraft))
      add(CodeReviewDetailsStatusComponentFactory.createNeedReviewerComponent(scope, reviewFlowVm.reviewerReviews))
      add(CodeReviewDetailsStatusComponentFactory.createReviewersReviewStateComponent(
        scope, reviewFlowVm.reviewerReviews,
        reviewerActionProvider = { reviewer ->
          DefaultActionGroup(GHPRRemoveReviewerAction(scope, project, reviewFlowVm, reviewer).toAnAction())
        },
        reviewerNameProvider = { reviewer -> reviewer.getPresentableName() },
        avatarKeyProvider = { reviewer -> reviewer.avatarUrl },
        iconProvider = { iconKey, iconSize -> avatarIconsProvider.getIcon(iconKey, iconSize) }
      ))
    }
    val scrollableStatusesPanel = ScrollPaneFactory.createScrollPane(statusesPanel, true).apply {
      isOpaque = false
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
      viewport.isOpaque = false
    }

    return Wrapper().apply {
      name = "Status check panel"
      bindContentIn(scope, reviewStatusVm.mergeabilityState.map { mergeability ->
        if (mergeability == null) loadingPanel else scrollableStatusesPanel
      })
    }
  }

  private fun createLoadingComponent(
    scope: CoroutineScope,
    reviewStatusVm: GHPRStatusViewModel,
    securityService: GHPRSecurityService
  ): JComponent {
    val stateLabel = CodeReviewDetailsStatusComponentFactory.ReviewDetailsStatusLabel("Pull request status: loading label").apply {
      border = JBUI.Borders.empty(5, 0)
      icon = if (ExperimentalUI.isNewUI()) ExpUiIcons.Run.TestNotRunYet else AllIcons.RunConfigurations.TestNotRan
      text = GithubBundle.message("pull.request.loading.status")
    }
    val accessDeniedLabel = createAccessDeniedLabel(scope, reviewStatusVm, securityService)
    return VerticalListPanel().apply {
      name = "Loading statuses panel"
      add(stateLabel)
      add(accessDeniedLabel)
    }
  }

  private fun createAccessDeniedLabel(
    scope: CoroutineScope,
    reviewStatusVm: GHPRStatusViewModel,
    securityService: GHPRSecurityService
  ): JComponent {
    val viewerDidAuthor = reviewStatusVm.viewerDidAuthor

    val mergeForbidden = securityService.isMergeForbiddenForProject()
    val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
    val canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerDidAuthor
    val canMarkReadyForReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || viewerDidAuthor

    return CodeReviewDetailsStatusComponentFactory.ReviewDetailsStatusLabel("Code review status: access denied").apply {
      border = JBUI.Borders.empty(5, 0)
      icon = if (ExperimentalUI.isNewUI()) ExpUiIcons.Status.Error else AllIcons.RunConfigurations.TestError
      bindTextIn(scope, reviewStatusVm.isDraft.map { isDraft ->
        when {
          !canClose -> GithubBundle.message("pull.request.repo.access.required")
          !canMarkReadyForReview && isDraft -> GithubBundle.message("pull.request.repo.write.access.required")
          !canMerge && !isDraft -> GithubBundle.message("pull.request.repo.write.access.required")
          mergeForbidden && !isDraft -> GithubBundle.message("pull.request.merge.disabled")
          else -> {
            isVisible = false
            return@map ""
          }
        }
      })
    }
  }
}