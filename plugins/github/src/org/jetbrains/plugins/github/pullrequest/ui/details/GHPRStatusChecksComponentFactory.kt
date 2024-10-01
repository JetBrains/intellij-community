// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.HorizontalListPanel
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsStatusComponentFactory
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.AnActionLink
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import git4idea.remote.hosting.ui.ResolveConflictsLocallyDialogComponentFactory.showBranchUpdateDialog
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.github.api.data.GHRepositoryPermissionLevel
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.action.GHPRRemoveReviewerAction
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRResolveConflictsLocallyError
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRResolveConflictsLocallyError.*
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JScrollPane

internal object GHPRStatusChecksComponentFactory {
  fun create(
    parentScope: CoroutineScope,
    project: Project,
    detailsVm: GHPRDetailsViewModel,
  ): JComponent {
    val scope = parentScope.childScope(Dispatchers.Main.immediate)

    val reviewStatusVm = detailsVm.statusVm
    val reviewFlowVm = detailsVm.reviewFlowVm
    val securityService = detailsVm.securityService
    val avatarIconsProvider = detailsVm.avatarIconsProvider

    val loadingPanel = createLoadingComponent(scope, reviewStatusVm, securityService)

    val actionManager = ActionManager.getInstance()
    val additionalActionsGroup = actionManager.getAction("Github.PullRequest.StatusChecks.AdditionalActions") as DefaultActionGroup
    val additionalActions = additionalActionsGroup.getChildren(actionManager).toList()

    val statusesPanel = VerticalListPanel().apply {
      add(createAccessDeniedLabel(scope, reviewStatusVm, securityService))
      add(CodeReviewDetailsStatusComponentFactory.createCiComponent(scope, reviewStatusVm))
      add(createConflictsStatusComponentIn(scope, reviewStatusVm.resolveConflictsVm))
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
        iconProvider = { reviewState, iconKey, iconSize ->
          CodeReviewAvatarUtils.createIconWithOutline(
            avatarIconsProvider.getIcon(iconKey, iconSize),
            ReviewDetailsUIUtil.getReviewStateIconBorder(reviewState)
          )
        }
      ))
      add(createAdditionalActionsPanel(additionalActions, detailsVm))
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

  private fun createConflictsStatusComponentIn(
    scope: CoroutineScope,
    resolveConflictsVm: ResolveConflictsLocallyViewModel<GHPRResolveConflictsLocallyError>,
  ) = CodeReviewDetailsStatusComponentFactory.createConflictsComponent(
    scope, resolveConflictsVm.hasConflicts,
    resolveConflictsVm.requestOrError.map { requestOrError ->
      requestOrError.bimap(
        ifLeft = {
          when (it) {
            is AlreadyResolvedLocally -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.alreadyResolvedLocally")
            is MergeInProgress -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.mergeInProgress")
            is DetailsNotLoaded -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.detailsNotLoaded")
            is RepositoryNotFound -> GithubBundle.message("pull.request.resolveConflicts.error.repositoryNotFound", it.baseOrHead.text)
            is RemoteNotFound -> GithubBundle.message("pull.request.resolveConflicts.error.remoteNotFound", it.baseOrHead.text, it.coordinates.getWebURI())
          }
        },
        ifRight = { request ->
          ActionListener {
            resolveConflictsVm.performResolveConflicts {
              withContext(Dispatchers.Main) {
                showBranchUpdateDialog(request.headRefName, request.baseRefName)
              }
            }
          }
        }
      )
    },
    resolveConflictsVm.isBusy
  )

  private fun createLoadingComponent(
    scope: CoroutineScope,
    reviewStatusVm: GHPRStatusViewModel,
    securityService: GHPRSecurityService,
  ): JComponent {
    val stateLabel = CodeReviewDetailsStatusComponentFactory.ReviewDetailsStatusLabel("Pull request status: loading label").apply {
      border = JBUI.Borders.empty(5, 0)
      icon = if (ExperimentalUI.isNewUI()) AllIcons.RunConfigurations.TestNotRan else AllIcons.RunConfigurations.TestNotRan
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
    securityService: GHPRSecurityService,
  ): JComponent {
    val viewerDidAuthor = reviewStatusVm.viewerDidAuthor

    val mergeForbidden = securityService.isMergeForbiddenForProject()
    val canMerge = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE)
    val canClose = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.TRIAGE) || viewerDidAuthor
    val canMarkReadyForReview = securityService.currentUserHasPermissionLevel(GHRepositoryPermissionLevel.WRITE) || viewerDidAuthor

    return CodeReviewDetailsStatusComponentFactory.ReviewDetailsStatusLabel("Code review status: access denied").apply {
      border = JBUI.Borders.empty(5, 0)
      icon = if (ExperimentalUI.isNewUI()) AllIcons.General.Error else AllIcons.RunConfigurations.TestError
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

  private fun createAdditionalActionsPanel(actions: List<AnAction>, detailsVm: GHPRDetailsViewModel): JComponent =
    VerticalListPanel().apply {
      for (action in actions) {
        add(HorizontalListPanel(8).apply {
          border = JBUI.Borders.empty(5, 0)

          add(JLabel().apply {
            icon = action.templatePresentation.icon ?: EmptyIcon.ICON_16
          })

          add(AnActionLink(action, "status").apply {
            icon = null
          })
        })
      }
    }
}