// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.icon.IconsProvider
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.collaboration.ui.util.swingAction
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestActionPlaces
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabCommit
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangeListViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.error.GitLabMergeRequestErrorStatusPresenter
import org.jetbrains.plugins.gitlab.ui.GitLabUIUtil
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
    return Wrapper(LoadingLabel()).apply {
      isOpaque = false
      background = UIUtil.getListBackground()

      bindContentIn(scope, detailsLoadingVm.mergeRequestLoadingFlow) { loadingState ->
        when (loadingState) {
          GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Loading -> LoadingLabel()
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error -> {
            val errorPresenter = GitLabMergeRequestErrorStatusPresenter(
              accountVm,
              swingAction(GitLabBundle.message("merge.request.reload")) {
                detailsLoadingVm.reloadData()
              })
            val errorPanel = ErrorStatusPanelFactory.create(scope, flowOf(loadingState.exception), errorPresenter)
            CollaborationToolsUIUtil.moveToCenter(errorPanel)
          }
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result -> {
            val detailsVm = loadingState.detailsVm
            val detailsPanel = createDetailsComponent(project, detailsVm, avatarIconsProvider).apply {
              val actionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Details.Popup") as ActionGroup
              PopupHandler.installPopupMenu(this, actionGroup, GitLabMergeRequestActionPlaces.DETAILS_POPUP)

              val changesModelState = detailsVm.changesVm.changeListVm.map { it.result?.getOrNull() }
                .stateIn(this@bindContentIn, SharingStarted.Eagerly, null)
              DataManager.registerDataProvider(this) { dataId ->
                when {
                  GitLabMergeRequestViewModel.DATA_KEY.`is`(dataId) -> detailsVm
                  GitLabMergeRequestChangeListViewModel.DATA_KEY.`is`(dataId) -> changesModelState.value
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
      add(CodeReviewDetailsCommitsComponentFactory.create(cs, changesVm) { commit ->
        createCommitInfoPresenter(commit)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(cs, branchesVm))
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
                                                             commitPresentation = { commit ->
                                                               createCommitInfoPresenter(commit) {
                                                                 GitLabUIUtil.convertToHtml(project, it)
                                                               }
                                                             },
                                                             htmlPaneFactory = { SimpleHtmlPane() }),
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(GitLabMergeRequestDetailsChangesComponentFactory.create(cs, changesVm),
          CC().grow().shrinkPrioY(200))
      add(GitLabMergeRequestDetailsStatusChecksComponentFactory.create(cs, statusVm, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(cs, detailsReviewFlowVm),
          CC().growX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))
    }
  }

  private fun createCommitInfoPresenter(commit: GitLabCommit, issueProcessor: ((String) -> String)? = null): CommitPresentation {
    val title = commit.fullTitle.orEmpty()
    val description = commit.description?.removePrefix(title).orEmpty()
    return CommitPresentation(
      titleHtml = if (issueProcessor != null) issueProcessor(title) else title,
      descriptionHtml = if (issueProcessor != null) issueProcessor(description) else description,
      author = commit.author?.name ?: commit.authorName,
      committedDate = commit.authoredDate
    )
  }
}