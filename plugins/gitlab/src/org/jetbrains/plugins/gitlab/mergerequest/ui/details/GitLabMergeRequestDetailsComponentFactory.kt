// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.codereview.details.ReviewDetailsUIUtil
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequestId
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsComponentFactory {
  fun create(
    scope: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsViewModel,
    backToListAction: () -> Unit,
    openTimeLineAction: (GitLabMergeRequestId, Boolean) -> Unit
  ): JComponent {
    val backToListActionLink = ActionLink(CollaborationToolsBundle.message("review.details.view.back.to.list")) {
      backToListAction()
    }
    // TODO: temporary empty panel
    val emptyPanel = JBUI.Panels.simplePanel().apply {
      background = UIUtil.getListBackground()
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

      add(GitLabMergeRequestDetailsTitleComponentFactory.create(scope, detailsVm),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           top = ReviewDetailsUIUtil.indentTop,
                           bottom = ReviewDetailsUIUtil.gapBetweenTitleAndDescription))
      add(GitLabMergeRequestDetailsDescriptionComponentFactory.create(scope, detailsVm, openTimeLineAction),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenDescriptionAndCommits))
      add(GitLabMergeRequestDetailsBranchComponentFactory.create(scope, detailsVm),
          CC().growX().gap(left = ReviewDetailsUIUtil.indentLeft,
                           right = ReviewDetailsUIUtil.indentRight,
                           bottom = ReviewDetailsUIUtil.gapBetweenCommitsAndCommitInfo))
      add(emptyPanel, CC().grow().push()) // TODO: remove
    }
  }
}