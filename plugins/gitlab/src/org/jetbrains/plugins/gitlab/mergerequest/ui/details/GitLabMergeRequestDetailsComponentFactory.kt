// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.api.dto.GitLabCommitDTO
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestsActionKeys
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.list.GitLabMergeRequestErrorStatusPresenter
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JPanel

internal object GitLabMergeRequestDetailsComponentFactory {
  fun createDetailsComponent(
    project: Project,
    scope: CoroutineScope,
    detailsLoadingVm: GitLabMergeRequestDetailsLoadingViewModel,
    accountVm: GitLabAccountViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
  ): JComponent {
    return Wrapper().apply {
      isOpaque = false
      background = UIUtil.getListBackground()

      bindContentIn(scope, detailsLoadingVm.mergeRequestLoadingFlow) { loadingState ->
        when (loadingState) {
          GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Loading -> LoadingLabel()
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error -> {
            val errorPresenter = GitLabMergeRequestErrorStatusPresenter(accountVm)
            val errorPanel = ErrorStatusPanelFactory.create(scope, flowOf(loadingState.exception), errorPresenter)
            CollaborationToolsUIUtil.moveToCenter(errorPanel)
          }
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result -> {
            val detailsVm = loadingState.detailsVm
            val detailsPanel = createDetailsComponent(project, detailsVm, avatarIconsProvider).apply {
              val actionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Details.Popup") as ActionGroup
              PopupHandler.installPopupMenu(this, actionGroup, ActionPlaces.POPUP)
              DataManager.registerDataProvider(this) { dataId ->
                when {
                  GitLabMergeRequestViewModel.DATA_KEY.`is`(dataId) -> detailsVm
                  GitLabMergeRequestChangesViewModel.DATA_KEY.`is`(dataId) -> detailsVm.changesVm
                  else -> null
                }
              }
            }

            CollaborationToolsUIUtil.wrapWithProgressStripe(scope, detailsVm.isLoading, detailsPanel)
          }
        }
      }
    }
  }

  private fun CoroutineScope.createDetailsComponent(
    project: Project,
    detailsVm: GitLabMergeRequestDetailsViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>
  ): JComponent {
    val cs = this
    val detailsReviewFlowVm = detailsVm.detailsReviewFlowVm
    val branchesVm = detailsVm.branchesVm
    val statusVm = detailsVm.statusVm
    val changesVm = detailsVm.changesVm

    val commitsAndBranches = JPanel(MigLayout(LC().emptyBorders().fill(), AC().gap("push"))).apply {
      isOpaque = false
      add(CodeReviewDetailsCommitsComponentFactory.create(cs, changesVm) { commit: GitLabCommitDTO? ->
        createCommitsPopupPresenter(commit, changesVm.reviewCommits.value.size)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(
        cs, branchesVm,
        checkoutAction = ActionManager.getInstance().getAction("GitLab.Merge.Request.Branch.Checkout.Remote"),
        dataContext = SimpleDataContext.builder()
          .add(GitLabMergeRequestsActionKeys.REVIEW_BRANCH_VM, branchesVm)
          .build()))
    }
    val actionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Details.Popup") as ActionGroup

    val layout = MigLayout(
      LC()
        .emptyBorders()
        .fill()
        .flowY()
        .noGrid()
        .hideMode(3)
    )

    return JPanel(layout).apply {
      isOpaque = true
      background = UIUtil.getListBackground()

      add(CodeReviewDetailsTitleComponentFactory.create(cs, detailsVm, GitLabBundle.message("open.on.gitlab.tooltip"), actionGroup,
                                                        htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.TITLE_GAPS))
      add(CodeReviewDetailsDescriptionComponentFactory.create(cs, detailsVm, actionGroup,
                                                              showTimelineAction = { _ -> detailsVm.showTimeline() },
                                                              htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.DESCRIPTION_GAPS))
      add(commitsAndBranches,
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_POPUP_BRANCHES_GAPS))
      add(CodeReviewDetailsCommitInfoComponentFactory.create(cs, changesVm.selectedCommit,
                                                             commitPresenter = { commit -> createCommitInfoPresenter(commit) },
                                                             htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(GitLabMergeRequestDetailsChangesComponentFactory(project).create(cs, changesVm),
          CC().grow().shrinkPrioY(200))
      add(GitLabMergeRequestDetailsStatusChecksComponentFactory.create(cs, statusVm, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(cs, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))
    }
  }

  private fun createCommitsPopupPresenter(commit: GitLabCommitDTO?, commitsCount: Int): CommitPresenter {
    return if (commit == null) {
      CommitPresenter.AllCommits(title = CollaborationToolsBundle.message("review.details.commits.popup.all", commitsCount))
    }
    else {
      createCommitInfoPresenter(commit)
    }
  }

  private fun createCommitInfoPresenter(commit: GitLabCommitDTO): CommitPresenter {
    val title = commit.fullTitle.orEmpty()
    val description = commit.description?.removePrefix(title).orEmpty()
    return CommitPresenter.SingleCommit(
      title = title,
      description = description,
      author = commit.author?.name ?: commit.authorName,
      committedDate = commit.authoredDate
    )
  }
}