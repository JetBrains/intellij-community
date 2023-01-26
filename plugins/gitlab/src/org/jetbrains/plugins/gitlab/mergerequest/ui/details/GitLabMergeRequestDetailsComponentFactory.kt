// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.icon.AsyncImageIconsProvider
import com.intellij.collaboration.ui.icon.CachingIconsProvider
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.api.GitLabProjectConnection
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.file.GitLabTimelinesController
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsComponentFactory {
  fun createDetailsComponent(
    project: Project,
    scope: CoroutineScope,
    connection: GitLabProjectConnection,
    detailsVm: GitLabMergeRequestDetailsLoadingViewModel
  ): JComponent {
    val repo = connection.repo.repository
    val wrapper = Wrapper()

    wrapper.bindContent(scope, detailsVm.mergeRequestLoadingFlow.map { loadingState ->
      when (loadingState) {
        GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Loading -> LoadingLabel()
        is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error -> SimpleHtmlPane(loadingState.exception.localizedMessage)
        is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result -> {
          val avatarIconsProvider: IconsProvider<GitLabUserDTO> = CachingIconsProvider(
            AsyncImageIconsProvider(scope, connection.imageLoader)
          )
          createDetailsComponent(
            scope,
            loadingState.detailsVm,
            avatarIconsProvider,
            openTimeLineAction = { mergeRequestId, focus -> GitLabTimelinesController.openTimeline(project, repo, mergeRequestId, focus) }
          )
        }
      }
    })

    return wrapper
  }

  private fun createDetailsComponent(
    scope: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    openTimeLineAction: (GitLabMergeRequestId, Boolean) -> Unit
  ): JComponent {
    val detailsInfoVm = detailsVm.detailsInfoVm
    val detailsReviewFlowVm = detailsVm.detailsReviewFlowVm
    val commitsVm = detailsVm.commitsVm

    // TODO: temporary empty panel
    val emptyPanel = JBUI.Panels.simplePanel().apply {
      background = UIUtil.getListBackground()
    }
    val commitsAndBranches = JPanel(HorizontalLayout(0)).apply {
      isOpaque = false
      add(GitLabMergeRequestDetailsCommitsComponentFactory.create(scope, commitsVm), HorizontalLayout.LEFT)
      add(GitLabMergeRequestDetailsBranchComponentFactory.create(scope, detailsInfoVm), HorizontalLayout.RIGHT)
    }

    val layout = MigLayout(
      LC()
        .emptyBorders()
        .fill()
        .flowY()
        .hideMode(3)
    )

    return JPanel(layout).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      add(GitLabMergeRequestDetailsTitleComponentFactory.create(scope, detailsInfoVm),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           top = ReviewDetailsUIUtil.indentTop,
                           bottom = ReviewDetailsUIUtil.gapBetweenTitleAndDescription))
      add(GitLabMergeRequestDetailsDescriptionComponentFactory.create(scope, detailsInfoVm, openTimeLineAction),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenDescriptionAndCommits))
      add(commitsAndBranches,
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenCommitsAndCommitInfo))

      add(GitLabMergeRequestDetailsCommitInfoComponentFactory.create(scope, commitsVm),
          CC().growX().maxHeight("${ReviewDetailsUIUtil.commitInfoMaxHeight}")
            .gap(left = ReviewDetailsUIUtil.indentLeft,
                 right = ReviewDetailsUIUtil.indentRight,
                 bottom = ReviewDetailsUIUtil.gapBetweenCommitInfoAndCommitsBrowser))
      add(emptyPanel, CC().grow().push()) // TODO: remove
      add(GitLabMergeRequestDetailsStatusChecksComponentFactory.create(scope, detailsInfoVm),
          CC().growX().maxHeight("${ReviewDetailsUIUtil.statusChecksMaxHeight}")
            .gap(left = ReviewDetailsUIUtil.indentLeft,
                 right = ReviewDetailsUIUtil.indentRight,
                 top = 4,
                 bottom = ReviewDetailsUIUtil.gapBetweenCheckAndActions))
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(scope, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft - 2,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.indentBottom))
    }
  }
}