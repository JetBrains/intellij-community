// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingScope
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.LoadingLabel
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangeListComponentFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPanelFactory
import com.intellij.collaboration.ui.codereview.list.error.ErrorStatusPresenter
import com.intellij.collaboration.ui.util.bindContentIn
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.EDITOR_TAB_DIFF_PREVIEW
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.ui.components.panels.Wrapper
import com.intellij.util.ui.UIUtil
import git4idea.repo.GitRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRCombinedDiffPreviewBase
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRBranchesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRChangeListViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRChangesViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRChangesViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRDetailsViewModel
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.GHPRReviewFlowViewModelImpl
import javax.swing.Action
import javax.swing.JComponent

internal class GHPRViewComponentFactory(actionManager: ActionManager,
                                        private val project: Project,
                                        private val dataContext: GHPRDataContext,
                                        pullRequest: GHPRIdentifier,
                                        private val disposable: Disposable) {
  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

  private val detailsLoadingModel = GHCompletableFutureLoadingModel<GHPullRequest>(disposable)

  init {
    dataProvider.detailsData.loadDetails(disposable) {
      detailsLoadingModel.future = it
    }
  }

  private val reloadDetailsAction = actionManager.getAction("Github.PullRequest.Details.Reload")

  private val detailsLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }

  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(): JComponent =
    createInfoComponent().apply {
      DataManager.registerDataProvider(this) { dataId ->
        when {
          GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> repository
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
          else -> null
        }
      }
    }

  private fun createInfoComponent(): JComponent {
    return GHLoadingPanelFactory(
      detailsLoadingModel,
      null, GithubBundle.message("cannot.load.details"),
      detailsLoadingErrorHandler
    ).createWithUpdatesStripe(uiDisposable) { _, model ->
      val scope = DisposingScope(disposable, SupervisorJob() + Dispatchers.Main.immediate)
      val reviewDetailsVm = GHPRDetailsViewModel(scope, model)
      val reviewBranchesVm = GHPRBranchesViewModel(scope, project, dataContext.repositoryDataService.repositoryMapping, model)
      val reviewStatusVm = GHPRStatusViewModelImpl(scope, project, model, dataProvider.stateData)
      val reviewFlowVm = GHPRReviewFlowViewModelImpl(scope,
                                                     project,
                                                     model,
                                                     dataContext.repositoryDataService,
                                                     dataContext.securityService,
                                                     dataContext.avatarIconsProvider,
                                                     dataProvider.detailsData,
                                                     dataProvider.stateData,
                                                     dataProvider.changesData,
                                                     dataProvider.reviewData,
                                                     disposable)
      val changesVm = GHPRChangesViewModelImpl(scope, project, dataContext, dataProvider)

      GHPRDetailsComponentFactory.create(scope,
                                         project,
                                         reviewDetailsVm, reviewBranchesVm, reviewStatusVm, reviewFlowVm, changesVm,
                                         dataProvider,
                                         dataContext.securityService, dataContext.avatarIconsProvider,
                                         scope.createChangesComponent(changesVm))
    }.apply {
      isOpaque = true
      background = UIUtil.getListBackground()
      reloadDetailsAction.registerCustomShortcutSet(this, uiDisposable)
    }
  }

  private fun CoroutineScope.createChangesComponent(changesVm: GHPRChangesViewModel): JComponent {
    val cs = this
    return Wrapper(LoadingLabel()).apply {
      isOpaque = false

      bindContentIn(cs, changesVm.changeListVm) { result ->
        result.fold(
          onSuccess = { createChangesPanel(changesVm, it) },
          onFailure = { createChangesErrorComponent(changesVm, it) }
        )
      }
    }
  }

  private fun CoroutineScope.createChangesPanel(changesVm: GHPRChangesViewModel,
                                                changeListVm: GHPRChangeListViewModel): JComponent {
    val tree = CodeReviewChangeListComponentFactory.createIn(this, changeListVm, changeListVm.progressModel,
                                                             GithubBundle.message("pull.request.does.not.contain.changes"))
    ClientProperty.put(tree, GHPRCommitBrowserComponentController.KEY, changesVm)

    val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
    val stripe = CollaborationToolsUIUtil.wrapWithProgressStripe(this, changeListVm.isUpdating, scrollPane)
    ScrollableContentBorder.setup(scrollPane, Side.TOP_AND_BOTTOM, stripe)

    DataManager.registerDataProvider(stripe) { dataId ->
      when {
        EDITOR_TAB_DIFF_PREVIEW.`is`(dataId) -> changeListVm
        tree.isShowing ->
          when {
            GHPRActionKeys.PULL_REQUEST_FILES.`is`(dataId) -> tree.getPullRequestFiles()
            GHPRChangeListViewModel.DATA_KEY.`is`(dataId) -> changeListVm
            else -> null
          } ?: tree.getData(dataId)
        else -> null
      }
    }
    tree.installPopupHandler(ActionManager.getInstance().getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    return stripe
  }

  private fun CoroutineScope.createChangesErrorComponent(changesVm: GHPRChangesViewModel, error: Throwable): JComponent {
    val errorPresenter = object : ErrorStatusPresenter<Throwable> {
      override fun getErrorTitle(error: Throwable): String = GithubBundle.message("cannot.load.changes")
      override fun getErrorDescription(error: Throwable): String? = error.localizedMessage
      override fun getErrorAction(error: Throwable): Action = changesVm.changesLoadingErrorHandler.getActionForError(error)
    }
    val errorPanel = ErrorStatusPanelFactory.create(this, flowOf(error), errorPresenter)
    return CollaborationToolsUIUtil.moveToCenter(errorPanel)
  }
}

private fun ChangesTree.getPullRequestFiles(): Iterable<FilePath> =
  VcsTreeModelData.selected(this)
    .iterateUserObjects(Change::class.java)
    .map { ChangesUtil.getFilePath(it) }
