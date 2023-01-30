// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContent
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.openapi.project.Project
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsComponentFactory {
  fun createDetailsComponent(
    project: Project,
    scope: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsLoadingViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val wrapper = Wrapper()

    wrapper.bindContent(scope, detailsVm.mergeRequestLoadingFlow) { contentCs, loadingState ->
      when (loadingState) {
        GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Loading -> LoadingLabel()
        is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error -> SimpleHtmlPane(loadingState.exception.localizedMessage)
        is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result -> {
          createDetailsComponent(
            project,
            contentCs,
            loadingState.detailsVm,
            avatarIconsProvider
          )
        }
      }
    }

    return wrapper
  }

  private fun createDetailsComponent(
    project: Project,
    cs: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val detailsInfoVm = detailsVm.detailsInfoVm
    val detailsReviewFlowVm = detailsVm.detailsReviewFlowVm
    val changesVm = detailsVm.changesVm

    val commitsAndBranches = JPanel(HorizontalLayout(0)).apply {
      isOpaque = false
      add(GitLabMergeRequestDetailsCommitsComponentFactory.create(cs, changesVm), HorizontalLayout.LEFT)
      add(GitLabMergeRequestDetailsBranchComponentFactory.create(cs, detailsInfoVm), HorizontalLayout.RIGHT)
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

      add(GitLabMergeRequestDetailsTitleComponentFactory.create(cs, detailsInfoVm),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           top = ReviewDetailsUIUtil.indentTop,
                           bottom = ReviewDetailsUIUtil.gapBetweenTitleAndDescription))
      add(GitLabMergeRequestDetailsDescriptionComponentFactory.create(cs, detailsInfoVm),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenDescriptionAndCommits))
      add(commitsAndBranches,
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenCommitsAndCommitInfo))

      add(GitLabMergeRequestDetailsCommitInfoComponentFactory.create(cs, changesVm.selectedCommit),
          CC().growX().maxHeight("${ReviewDetailsUIUtil.commitInfoMaxHeight}")
            .gap(left = ReviewDetailsUIUtil.indentLeft,
                 right = ReviewDetailsUIUtil.indentRight,
                 bottom = ReviewDetailsUIUtil.gapBetweenCommitInfoAndCommitsBrowser))
      add(GitLabMergeRequestDetailsChangesComponentFactory(project).create(cs, changesVm),
          CC().grow().push())
      add(GitLabMergeRequestDetailsStatusChecksComponentFactory.create(cs, detailsInfoVm),
          CC().growX().maxHeight("${ReviewDetailsUIUtil.statusChecksMaxHeight}")
            .gap(left = ReviewDetailsUIUtil.indentLeft,
                 right = ReviewDetailsUIUtil.indentRight,
                 top = 4,
                 bottom = ReviewDetailsUIUtil.gapBetweenCheckAndActions))
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(cs, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft - 2,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.indentBottom))
    }
  }
}