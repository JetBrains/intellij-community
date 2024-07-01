// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.avatar.CodeReviewAvatarUtils
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsStatusComponentFactory
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ScrollPaneFactory
import git4idea.remote.hosting.ui.ResolveConflictsLocallyDialogComponentFactory.showBranchUpdateDialog
import git4idea.remote.hosting.ui.ResolveConflictsLocallyViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestRemoveReviewerAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestStatusViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabResolveConflictsLocallyError
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabResolveConflictsLocallyError.*
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import java.awt.event.ActionListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JScrollPane

internal object GitLabMergeRequestDetailsStatusChecksComponentFactory {
  fun create(
    scope: CoroutineScope,
    statusVm: GitLabMergeRequestStatusViewModel,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ): JComponent {
    val statuses = VerticalListPanel().apply {
      add(CodeReviewDetailsStatusComponentFactory.createCiComponent(scope, statusVm))
      add(createConflictsStatusComponentIn(scope, statusVm.resolveConflictsVm))
      add(CodeReviewDetailsStatusComponentFactory.createRequiredResolveConversationsComponent(
        scope, statusVm.requiredConversationsResolved
      ))
      add(CodeReviewDetailsStatusComponentFactory.createNeedReviewerComponent(scope, reviewFlowVm.reviewerReviews))
      add(CodeReviewDetailsStatusComponentFactory.createReviewersReviewStateComponent(
        scope, reviewFlowVm.reviewerReviews,
        reviewerActionProvider = { reviewer ->
          DefaultActionGroup(GitLabMergeRequestRemoveReviewerAction(scope, reviewFlowVm, reviewer).toAnAction())
        },
        reviewerNameProvider = { reviewer -> reviewer.name },
        avatarKeyProvider = { reviewer -> reviewer },
        iconProvider = { reviewState, iconKey, iconSize ->
          CodeReviewAvatarUtils.createIconWithOutline(
            avatarIconsProvider.getIcon(iconKey, iconSize),
            ReviewDetailsUIUtil.getReviewStateIconBorder(reviewState)
          )
        }
      ))
    }

    return ScrollPaneFactory.createScrollPane(statuses, true).apply {
      isOpaque = false
      horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
      viewport.isOpaque = false
    }.also {
      statuses.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          it.parent?.revalidate()
        }

        override fun componentMoved(e: ComponentEvent?) {
          it.parent?.revalidate()
        }
      })
    }
  }

  private fun createConflictsStatusComponentIn(
    scope: CoroutineScope,
    resolveConflictsVm: ResolveConflictsLocallyViewModel<GitLabResolveConflictsLocallyError>,
  ) = CodeReviewDetailsStatusComponentFactory.createConflictsComponent(
    scope, resolveConflictsVm.hasConflicts,
    resolveConflictsVm.requestOrError.map { requestOrError ->
      requestOrError.bimap(
        ifLeft = {
          when (it) {
            is AlreadyResolvedLocally -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.alreadyResolvedLocally")
            is MergeInProgress -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.mergeInProgress")
            is DetailsNotLoaded -> CollaborationToolsBundle.message("review.details.resolveConflicts.error.detailsNotLoaded")
            is SourceRepositoryNotFound -> GitLabBundle.message("merge.request.resolveConflicts.error.sourceProjectNotFound")
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
}