// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.VerticalListPanel
import com.intellij.collaboration.ui.codereview.details.CodeReviewDetailsStatusComponentFactory
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewStatusViewModel
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.toAnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestRemoveReviewerAction
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestReviewFlowViewModel
import javax.swing.JComponent

internal object GitLabMergeRequestDetailsStatusChecksComponentFactory {
  private const val STATUSES_GAP = 10

  fun create(
    scope: CoroutineScope,
    statusVm: CodeReviewStatusViewModel,
    reviewFlowVm: GitLabMergeRequestReviewFlowViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    return VerticalListPanel(STATUSES_GAP).apply {
      add(CodeReviewDetailsStatusComponentFactory.createCiComponent(scope, statusVm))
      add(CodeReviewDetailsStatusComponentFactory.createConflictsComponent(scope, statusVm.hasConflicts))
      add(CodeReviewDetailsStatusComponentFactory.createNeedReviewerComponent(scope, reviewFlowVm.reviewerReviews))
      add(CodeReviewDetailsStatusComponentFactory.createReviewersReviewStateComponent(
        scope, reviewFlowVm.reviewerReviews,
        reviewerActionProvider = { reviewer ->
          DefaultActionGroup(GitLabMergeRequestRemoveReviewerAction(scope, reviewFlowVm, reviewer).toAnAction())
        },
        reviewerNameProvider = { reviewer -> reviewer.name },
        avatarKeyProvider = { reviewer -> reviewer },
        iconProvider = { iconKey, iconSize -> avatarIconsProvider.getIcon(iconKey, iconSize) }
      ))
    }
  }
}