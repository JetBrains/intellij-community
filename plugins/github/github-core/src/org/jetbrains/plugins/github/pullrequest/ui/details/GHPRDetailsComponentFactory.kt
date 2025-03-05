// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.ActionLink
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.ui.util.addGithubHyperlinkListener
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponentFactory {

  fun create(
    scope: CoroutineScope,
    project: Project,
    detailsVm: GHPRDetailsViewModel,
    commitFilesBrowserComponent: JComponent
  ): JComponent {
    val actionGroup = ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup") as ActionGroup
    val title = CodeReviewDetailsTitleComponentFactory.create(scope, detailsVm, GithubBundle.message("open.on.github.action"), actionGroup,
                                                              htmlPaneFactory = { SimpleHtmlPane() })
    val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
      showTimelineAction(it.source as JComponent)
    }

    val commitsAndBranches = JPanel(MigLayout(LC().emptyBorders().fill(), AC().gap("push"))).apply {
      isOpaque = false
      add(CodeReviewDetailsCommitsComponentFactory.create(scope, detailsVm.changesVm) { commit: GHCommit ->
        createCommitsPopupPresenter(project, commit, detailsVm.securityService.ghostUser)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(scope, detailsVm.branchesVm))
    }
    val statusChecks = GHPRStatusChecksComponentFactory.create(scope, project, detailsVm)
    val actionsComponent = GHPRDetailsActionsComponentFactory.create(scope, project, detailsVm.reviewRequestState, detailsVm.reviewFlowVm)

    return JPanel(MigLayout(
      LC()
        .emptyBorders()
        .fill()
        .flowY()
        .noGrid()
        .hideMode(3)
    )).apply {
      isOpaque = false

      add(ReviewDetailsUIUtil.createTitlePanel(title, timelineLink), CC().growX().gap(ReviewDetailsUIUtil.TITLE_GAPS))
      add(commitsAndBranches, CC().growX().gap(ReviewDetailsUIUtil.COMMIT_POPUP_BRANCHES_GAPS))
      add(CodeReviewDetailsCommitInfoComponentFactory.create(
        scope, detailsVm.changesVm.selectedCommit,
        commitPresentation = { commit ->
          createCommitsPopupPresenter(project, commit, detailsVm.securityService.ghostUser)
        },
        htmlPaneFactory = {
          SimpleHtmlPane(addBrowserListener = false).apply {
            addGithubHyperlinkListener(detailsVm::openPullRequestInfoAndTimeline)
          }
        }),
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(commitFilesBrowserComponent, CC().grow().shrinkPrioY(200))
      add(statusChecks, CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(actionsComponent, CC().growX().pushX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))

      PopupHandler.installPopupMenu(this, actionGroup, ActionPlaces.POPUP)
    }
  }

  private fun showTimelineAction(parentComponent: JComponent) {
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return
    ActionUtil.invokeAction(action, parentComponent, ActionPlaces.UNKNOWN, null, null)
  }

  private fun createCommitsPopupPresenter(
    project: Project,
    commit: GHCommit,
    ghostUser: GHUser
  ) = CommitPresentation(
    titleHtml = commit.messageHeadline.convertToHtml(project),
    descriptionHtml = commit.messageBody.convertToHtml(project),
    author = (commit.author?.user ?: ghostUser).getPresentableName(),
    committedDate = commit.committedDate
  )
}