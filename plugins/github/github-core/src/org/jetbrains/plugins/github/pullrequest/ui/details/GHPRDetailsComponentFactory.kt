// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.details

import com.intellij.collaboration.messages.CollaborationToolsBundle
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.SimpleHtmlPane
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModelFromDetails
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory
import com.intellij.collaboration.ui.codereview.details.*
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.collaboration.ui.util.emptyBorders
import com.intellij.collaboration.ui.util.gap
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.Wrapper
import kotlinx.coroutines.CoroutineScope
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.GHUser
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.comment.convertToHtml
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRChangesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.ui.util.addGithubHyperlinkListener
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
object GHPRDetailsComponentFactory {

  fun create(
    scope: CoroutineScope,
    project: Project,
    detailsVm: GHPRDetailsViewModel,
    withTitle: Boolean = true,
  ): JComponent {
    val actionGroup = ActionManager.getInstance().getAction("Github.PullRequest.Details.Popup") as ActionGroup

    val titlePanel = if (withTitle) {
      val title = CodeReviewDetailsTitleComponentFactory.create(scope, detailsVm, GithubBundle.message("open.on.github.action"), actionGroup,
                                                                htmlPaneFactory = { SimpleHtmlPane() })
      val timelineLink = ActionLink(CollaborationToolsBundle.message("review.details.view.timeline.action")) {
        showTimelineAction(it.source as JComponent)
      }
      ReviewDetailsUIUtil.createTitlePanel(title, timelineLink)
    }
    else {
      null
    }

    val commitsAndBranches = createCommitsAndBranchesComponent(project, scope, detailsVm)
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

      if (titlePanel != null) {
        add(titlePanel, CC().growX().gap(ReviewDetailsUIUtil.TITLE_GAPS))
      }
      add(commitsAndBranches, CC().growX().gap(ReviewDetailsUIUtil.COMMIT_POPUP_BRANCHES_GAPS))
      add(createCommitsInfoComponent(project, scope, detailsVm), CC().growX().gap(ReviewDetailsUIUtil.COMMIT_INFO_GAPS))
      add(createCommitFilesBrowserComponent(scope, detailsVm.changesVm), CC().grow().shrinkPrioY(200))
      add(statusChecks, CC().growX().gap(ReviewDetailsUIUtil.STATUSES_GAPS).maxHeight("${ReviewDetailsUIUtil.STATUSES_MAX_HEIGHT}"))
      add(actionsComponent, CC().growX().pushX().gap(ReviewDetailsUIUtil.ACTIONS_GAPS).minHeight("pref"))

      PopupHandler.installPopupMenu(this, actionGroup, ActionPlaces.POPUP)
    }
  }

  private fun createCommitsInfoComponent(project: Project, cs: CoroutineScope, detailsVm: GHPRDetailsViewModel): JComponent {
    return CodeReviewDetailsCommitInfoComponentFactory.create(
      cs, detailsVm.changesVm.selectedCommit,
      commitPresentation = { commit ->
        createCommitsPopupPresenter(project, commit, detailsVm.securityService.ghostUser)
      },
      htmlPaneFactory = {
        SimpleHtmlPane(addBrowserListener = false).apply {
          addGithubHyperlinkListener(detailsVm::openPullRequestInfoAndTimeline)
        }
      })
  }

  private fun createCommitsAndBranchesComponent(project: Project, cs: CoroutineScope, detailsVm: GHPRDetailsViewModel): JComponent {
    return JPanel(MigLayout(LC().emptyBorders().fill(), AC().gap("push"))).apply {
      isOpaque = false
      add(CodeReviewDetailsCommitsComponentFactory.create(cs, detailsVm.changesVm) { commit: GHCommit ->
        createCommitsPopupPresenter(project, commit, detailsVm.securityService.ghostUser)
      })
      add(CodeReviewDetailsBranchComponentFactory.create(cs, detailsVm.branchesVm))
    }
  }

  private fun showTimelineAction(parentComponent: JComponent) {
    val action = ActionManager.getInstance().getAction("Github.PullRequest.Timeline.Show") ?: return
    ActionUtil.invokeAction(action, parentComponent, ActionPlaces.UNKNOWN, null, null)
  }

  private fun createCommitsPopupPresenter(
    project: Project,
    commit: GHCommit,
    ghostUser: GHUser,
  ) = CommitPresentation(
    titleHtml = commit.messageHeadline.convertToHtml(project),
    descriptionHtml = commit.messageBody.convertToHtml(project),
    author = (commit.author?.user ?: ghostUser).getPresentableName(),
    committedDate = commit.committedDate
  )


  private fun createCommitFilesBrowserComponent(cs: CoroutineScope, changesVm: GHPRChangesViewModel): JComponent {
    return Wrapper(LoadingLabel()).apply {
      bindContentIn(cs, changesVm.changeListVm) { res ->
        res.result?.let {
          it.fold(onSuccess = {
            createChangesPanel(it)
          }, onFailure = {
            createChangesErrorComponent(changesVm, it)
          })
        } ?: LoadingLabel()
      }
    }
  }

  private fun CoroutineScope.createChangesPanel(changeListVm: GHPRChangeListViewModel): JComponent {
    val progressModel = CodeReviewProgressTreeModelFromDetails(this, changeListVm)
    val tree = CodeReviewChangeListComponentFactory.createIn(this, changeListVm, progressModel,
                                                             GithubBundle.message("pull.request.does.not.contain.changes"))

    val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)

    DataManager.registerDataProvider(scrollPane) { dataId ->
      when {
        tree.isShowing ->
          when {
            GHPRChangeListViewModel.DATA_KEY.`is`(dataId) -> changeListVm
            CodeReviewChangeListViewModel.DATA_KEY.`is`(dataId) -> changeListVm
            else -> null
          } ?: tree.getData(dataId)
        else -> null
      }
    }
    tree.installPopupHandler(ActionManager.getInstance().getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    return scrollPane
  }

  private fun createChangesErrorComponent(changesVm: GHPRChangesViewModel, error: Throwable): JComponent {
    val errorPresenter = ErrorStatusPresenter.simple(
      GithubBundle.message("cannot.load.changes"),
      actionProvider = changesVm.changesLoadingErrorHandler::getActionForError
    )
    val errorPanel = ErrorStatusPanelFactory.create(error, errorPresenter)
    return CollaborationToolsUIUtil.moveToCenter(errorPanel)
  }
}