// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.data.loaders.GitLabProjectDetailsLoader
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsComponentFactory {
  fun create(
    scope: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsViewModel,
    projectDetailsLoader: GitLabProjectDetailsLoader,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    backToListAction: () -> Unit,
    openTimeLineAction: (GitLabMergeRequestId, Boolean) -> Unit
  ): JComponent {
    val detailsInfoVm = detailsVm.detailsInfoVm
    val detailsReviewFlowVm = detailsVm.detailsReviewFlowVm
    val commitsVm = detailsVm.commitsVm

    val backToListActionLink = ActionLink(CollaborationToolsBundle.message("review.details.view.back.to.list")) {
      backToListAction()
    }
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

      add(backToListActionLink, CC().growX())

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
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(scope, detailsReviewFlowVm, projectDetailsLoader, avatarIconsProvider),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft - 2,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.indentBottom))
    }
  }
}