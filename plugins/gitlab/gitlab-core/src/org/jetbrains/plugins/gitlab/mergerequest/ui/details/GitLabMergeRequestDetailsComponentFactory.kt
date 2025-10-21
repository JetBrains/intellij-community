// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewBranchesViewModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
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
import com.intellij.ui.components.ActionLink
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
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gitlab.api.dto.GitLabUserDTO
import org.jetbrains.plugins.gitlab.authentication.GitLabLoginSource
import org.jetbrains.plugins.gitlab.authentication.accounts.GitLabAccountViewModel
import org.jetbrains.plugins.gitlab.mergerequest.action.GitLabMergeRequestActionPlaces
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabCommitViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestChangesViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsLoadingViewModel
import org.jetbrains.plugins.gitlab.mergerequest.ui.details.model.GitLabMergeRequestDetailsViewModel
import org.jetbrains.plugins.gitlab.mergerequest.util.GitLabMergeRequestErrorUtil
import org.jetbrains.plugins.gitlab.mergerequest.util.addGitLabHyperlinkListener
import org.jetbrains.plugins.gitlab.util.GitLabBundle
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
object GitLabMergeRequestDetailsComponentFactory {
  fun createDetailsComponent(
    project: Project,
    scope: CoroutineScope,
    detailsLoadingVm: GitLabMergeRequestDetailsLoadingViewModel,
    accountVm: GitLabAccountViewModel,
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    withTitle: Boolean = true,
  ): JComponent {
    return Wrapper(LoadingLabel()).apply {
      isOpaque = false
      background = UIUtil.getListBackground()

      bindContentIn(scope, detailsLoadingVm.mergeRequestLoadingFlow) { loadingState ->
        when (loadingState) {
          GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Loading -> LoadingLabel()
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error -> {
            createErrorComponent(scope, detailsLoadingVm, loadingState, accountVm)
          }
          is GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Result -> {
            val detailsVm = loadingState.detailsVm
            val detailsPanel = createDetailsComponent(project, detailsVm, avatarIconsProvider, withTitle).apply {
              setupDetailsComponent(this@bindContentIn, detailsVm)
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
    avatarIconsProvider: IconsProvider<GitLabUserDTO>,
    withTitle: Boolean,
  ): JComponent {
    val cs = this
    val detailsReviewFlowVm = detailsVm.detailsReviewFlowVm
    val changesVm = detailsVm.changesVm

    val actionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Details.Popup") as ActionGroup

    val titlePanel = if (withTitle) {
      val title = CodeReviewDetailsTitleComponentFactory.create(cs, detailsVm, GitLabBundle.message("open.on.gitlab.tooltip"), actionGroup,
                                                                htmlPaneFactory = {
                                                                  SimpleHtmlPane(addBrowserListener = false).apply {
                                                                    addGitLabHyperlinkListener(project)
                                                                  }
                                                                })
      val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
        detailsVm.showTimeline()
      }
      ReviewDetailsUIUtil.createTitlePanel(title, timelineLink)
    }
    else {
      null
    }

    val commitsAndBranches = createCommitAndBranchesComponent(cs, changesVm, detailsVm.branchesVm)

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


      if (titlePanel != null) {
        add(titlePanel,
            CC().growX().gap(ReviewDetailsUIUtil.TITLE_GAPS))
      }
      add(commitsAndBranches,
          CC().growX().gap(ReviewDetailsUIUtil.COMMIT_POPUP_BRANCHES_GAPS))
      add(createChangesComponent(project, cs, changesVm), CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(GitLabMergeRequestDetailsChangesComponentFactory.create(cs, changesVm),
          CC().grow().shrinkPrioY(200))
      add(GitLabMergeRequestDetailsStatusChecksComponentFactory.create(cs, detailsVm.statusVm, detailsReviewFlowVm, avatarIconsProvider),
          CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(GitLabMergeRequestDetailsActionsComponentFactory.create(cs, detailsReviewFlowVm),
          CC().growX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))
    }
  }

  private fun createErrorComponent(
    cs: CoroutineScope,
    detailsLoadingVm: GitLabMergeRequestDetailsLoadingViewModel,
    loadingState: GitLabMergeRequestDetailsLoadingViewModel.LoadingState.Error,
    accountVm: GitLabAccountViewModel,
  ): JComponent {
    val errorPresenter = GitLabMergeRequestErrorUtil.createErrorStatusPresenter(
      accountVm,
      swingAction(GitLabBundle.message("merge.request.reload")) {
        detailsLoadingVm.reloadData()
      },
      GitLabLoginSource.MR_DETAILS)
    val errorPanel = ErrorStatusPanelFactory.create(cs, flowOf(loadingState.exception), errorPresenter)
    CollaborationToolsUIUtil.moveToCenter(errorPanel)
    return errorPanel
  }

  private fun JComponent.setupDetailsComponent(
    cs: CoroutineScope,
    detailsVm: GitLabMergeRequestDetailsViewModel,
  ) {
    val actionGroup = ActionManager.getInstance().getAction("GitLab.Merge.Request.Details.Popup") as ActionGroup
    PopupHandler.installPopupMenu(this, actionGroup, GitLabMergeRequestActionPlaces.DETAILS_POPUP)

    val changesModelState = detailsVm.changesVm.changeListVm.map { it.result?.getOrNull() }
      .stateIn(cs, SharingStarted.Eagerly, null)
    DataManager.registerDataProvider(this) { dataId ->
      when {
        GitLabMergeRequestViewModel.DATA_KEY.`is`(dataId) -> detailsVm
        CodeReviewChangeListViewModel.DATA_KEY.`is`(dataId) -> changesModelState.value
        else -> null
      }
    }
  }

  private fun createCommitAndBranchesComponent(
    cs: CoroutineScope,
    changesVm: GitLabMergeRequestChangesViewModel,
    branchesVm: CodeReviewBranchesViewModel,
  ): JComponent {
    return JPanel(MigLayout(LC().emptyBorders().fill(), AC().gap("push"))).apply {
      isOpaque = false
      add(CodeReviewDetailsCommitsComponentFactory.create(cs, changesVm) { commit ->
        createCommitInfoPresenter(commit)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(cs, branchesVm))
    }
  }

  private fun createChangesComponent(project: Project, cs: CoroutineScope, changesVm: GitLabMergeRequestChangesViewModel): JComponent {
    return CodeReviewDetailsCommitInfoComponentFactory.create(
      cs, changesVm.selectedCommit,
      commitPresentation = { commit ->
        createCommitInfoPresenter(commit)
      },
      htmlPaneFactory = {
        SimpleHtmlPane(addBrowserListener = false).apply {
          addGitLabHyperlinkListener(project)
        }
      }
    )
  }

  private fun createCommitInfoPresenter(commit: GitLabCommitViewModel): CommitPresentation {
    return CommitPresentation(
      titleHtml = commit.titleHtml.orEmpty(),
      descriptionHtml = commit.descriptionHtml.orEmpty(),
      author = commit.author,
      committedDate = commit.authoredDate
    )
  }
}