// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewDetailsViewModel
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.ui.PopupHandler
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.provider.GHPRDataProvider
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRSecurityService
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRReviewFlowViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRCommitsViewModel
import org.jetbrains.plugins.github.ui.avatars.GHAvatarIconsProvider
import javax.swing.JComponent
import javax.swing.JPanel

internal object GHPRDetailsComponentFactory {

  fun create(
    scope: CoroutineScope,
    reviewDetailsVm: CodeReviewDetailsViewModel,
    branchesVm: CodeReviewBranchesViewModel,
    reviewStatusVm: GHPRStatusViewModel,
    reviewFlowVm: GHPRReviewFlowViewModel,
    commitsVm: GHPRCommitsViewModel,
    dataProvider: GHPRDataProvider,
    securityService: GHPRSecurityService,
    avatarIconsProvider: GHAvatarIconsProvider,
    commitFilesBrowserComponent: JComponent
  ): JComponent {
    val commitsAndBranches = JPanel(MigLayout(LC().emptyBorders().fill(), AC().gap("push"))).apply {
      isOpaque = false
      add(CodeReviewDetailsCommitsComponentFactory.create(scope, commitsVm) { commit: GHCommit ->
        createCommitsPopupPresenter(commit, securityService.ghostUser)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(
        scope, branchesVm,
        checkoutAction = ActionManager.getInstance().getAction("Github.PullRequest.Branch.Checkout.Remote"),
        dataContext = SimpleDataContext.builder()
          .add(GHPRActionKeys.REVIEW_BRANCH_VM, branchesVm)
          .build()))
    }
    val statusChecks = GHPRStatusChecksComponentFactory.create(scope, reviewStatusVm, reviewFlowVm, securityService, avatarIconsProvider)
    val actionsComponent = GHPRDetailsActionsComponentFactory.create(scope, reviewDetailsVm.reviewRequestState, reviewFlowVm, dataProvider)
    val actionGroup = ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup") as ActionGroup

    return JPanel(MigLayout(
      LC()
        .emptyBorders()
        .fill()
        .flowY()
        .hideMode(3)
    )).apply {
      isOpaque = false

      add(CodeReviewDetailsTitleComponentFactory.create(scope, reviewDetailsVm, GithubBundle.message("open.on.github.action"), actionGroup,
                                                        htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.TITLE_GAPS))
      add(CodeReviewDetailsDescriptionComponentFactory.create(scope, reviewDetailsVm, actionGroup, ::showTimelineAction,
                                                              htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.DESCRIPTION_GAPS))
      add(commitsAndBranches, CC().growX().gap(ReviewDetailsUIUtil.COMMIT_POPUP_BRANCHES_GAPS))
      add(CodeReviewDetailsCommitInfoComponentFactory.create(scope, commitsVm.selectedCommit,
                                                             commitPresentation = { commit ->
                                                               createCommitsPopupPresenter(commit, commitsVm.ghostUser)
                                                             },
                                                             htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(commitFilesBrowserComponent, CC().grow().push())
      add(statusChecks, CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(actionsComponent, CC().growX().pushX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))

      PopupHandler.installPopupMenu(this, actionGroup, ActionPlaces.POPUP)
    }
  }

  private fun showTimelineAction(parentComponent: JComponent) {
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return
    ActionUtil.invokeAction(action, parentComponent, ActionPlaces.UNKNOWN, null, null)
  }

  private fun createCommitsPopupPresenter(commit: GHCommit, ghostUser: GHUser) = CommitPresentation(
    title = commit.messageHeadlineHTML,
    description = commit.messageBodyHTML,
    author = (commit.author?.user ?: ghostUser).getPresentableName(),
    committedDate = commit.committedDate
  )
}