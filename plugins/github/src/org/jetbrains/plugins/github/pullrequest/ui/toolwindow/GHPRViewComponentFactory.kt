// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingScope
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.changes.CodeReviewChangesTreeFactory
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.EDITOR_TAB_DIFF_PREVIEW
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.ClientProperty
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.ScrollableContentBorder
import com.intellij.ui.Side
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.changes.GitBranchComparisonResult
import git4idea.changes.findCumulativeChange
import git4idea.repo.GitRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRCombinedDiffPreviewBase.Companion.createAndSetupDiffPreview
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRDiffRequestChainProducer
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupport
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRViewedStateDiffSupportImpl
import org.jetbrains.plugins.github.pullrequest.ui.changes.showPullRequestProgress
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.model.GHPRStatusViewModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.*
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

internal class GHPRViewComponentFactory(private val actionManager: ActionManager,
                                        private val project: Project,
                                        private val dataContext: GHPRDataContext,
                                        pullRequest: GHPRIdentifier,
                                        private val disposable: Disposable) {
  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

  private val detailsLoadingModel = GHCompletableFutureLoadingModel<GHPullRequest>(disposable)
  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(disposable)
  private val changesLoadingModel = GHCompletableFutureLoadingModel<GitBranchComparisonResult>(disposable)
  private val viewedStateLoadingModel = GHCompletableFutureLoadingModel<Map<String, GHPullRequestFileViewedState>>(disposable)

  init {
    dataProvider.detailsData.loadDetails(disposable) {
      detailsLoadingModel.future = it
    }
    dataProvider.changesData.loadCommitsFromApi(disposable) {
      commitsLoadingModel.future = it
    }
    dataProvider.changesData.loadChanges(disposable) {
      changesLoadingModel.future = it
    }
    setupViewedStateModel()
    // pre-fetch to show diff quicker
    dataProvider.changesData.fetchBaseBranch()
    dataProvider.changesData.fetchHeadBranch()
  }

  private val reloadDetailsAction = actionManager.getAction("Github.PullRequest.Details.Reload")

  private val detailsLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }
  private val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private val diffRequestProducer: GHPRDiffRequestChainProducer =
    object : GHPRDiffRequestChainProducer(project,
                                          dataProvider,
                                          dataContext.htmlImageLoader, dataContext.avatarIconsProvider,
                                          dataContext.repositoryDataService,
                                          dataContext.securityService.ghostUser,
                                          dataContext.securityService.currentUser) {

      private val viewedStateSupport = GHPRViewedStateDiffSupportImpl(repository, dataProvider.viewedStateData)

      override fun createCustomContext(change: Change): Map<Key<*>, Any> {
        if (diffBridge.activeTree != GHPRDiffController.ActiveTree.FILES) return emptyMap()

        return mapOf(
          GHPRViewedStateDiffSupport.KEY to viewedStateSupport,
          GHPRViewedStateDiffSupport.PULL_REQUEST_FILE to ChangesUtil.getFilePath(change)
        )
      }
    }
  private val diffBridge = GHPRDiffController(dataProvider.diffRequestModel, diffRequestProducer)

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  private fun setupViewedStateModel() {
    fun update() {
      viewedStateLoadingModel.future = dataProvider.viewedStateData.loadViewedState()
    }

    dataProvider.viewedStateData.addViewedStateListener(disposable) { update() }
    update()
  }

  fun create(): JComponent =
    createInfoComponent().apply {
      DataManager.registerDataProvider(this) { dataId ->
        when {
          GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> repository
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
          DiffRequestChainProducer.DATA_KEY.`is`(dataId) -> diffRequestProducer
          else -> null
        }
      }
    }

  private class Controller(
    private val tree: AsyncChangesTree,
    private val changesProviderModel: SingleValueModel<GitBranchComparisonResult>,
    private val commitsVm: GHPRCommitsViewModel
  ) : GHPRCommitBrowserComponentController {
    override fun selectCommit(oid: String) {
      val selectedCommit = commitsVm.reviewCommits.value.find { it.abbreviatedOid == oid }
      commitsVm.selectCommit(selectedCommit)
      CollaborationToolsUIUtil.focusPanel(tree)
    }

    override fun selectChange(oid: String?, filePath: String) {
      commitsVm.selectAllCommits()

      tree.invokeAfterRefresh {
        if (oid == null) {
          tree.selectFile(VcsUtil.getFilePath(filePath, false))
        }
        else {
          val change = changesProviderModel.value.findCumulativeChange(oid, filePath)
          if (change == null) {
            tree.selectFile(VcsUtil.getFilePath(filePath, false))
          }
          else {
            tree.selectChange(change)
          }
        }
      }
      CollaborationToolsUIUtil.focusPanel(tree)
    }
  }

  private fun createInfoComponent(): JComponent {
    return GHLoadingPanelFactory(
      detailsLoadingModel,
      null, GithubBundle.message("cannot.load.details"),
      detailsLoadingErrorHandler
    ).createWithUpdatesStripe(uiDisposable) { _, model ->
      val branchesModel = GHPRBranchesModelImpl(model, dataProvider.detailsData, repository, disposable)
      val detailsModel = GHPRDetailsModelImpl(model)
      val metadataModel = GHPRMetadataModelImpl(model,
                                                dataContext.securityService,
                                                dataContext.repositoryDataService,
                                                dataProvider.detailsData)
      val stateModel = GHPRStateModelImpl(project, dataProvider.stateData, dataProvider.changesData, model, disposable)

      val scope = DisposingScope(disposable, SupervisorJob() + Dispatchers.Main.immediate)
      val reviewDetailsVm = GHPRDetailsViewModelImpl(detailsModel, stateModel)
      val reviewStatusVm = GHPRStatusViewModelImpl(stateModel)
      val reviewFlowVm = GHPRReviewFlowViewModelImpl(scope,
                                                     metadataModel,
                                                     stateModel,
                                                     dataContext.securityService,
                                                     dataContext.avatarIconsProvider,
                                                     dataProvider.detailsData,
                                                     dataProvider.reviewData,
                                                     disposable)
      val commitsVm = GHPRCommitsViewModel(scope, commitsLoadingModel, dataContext.securityService, diffBridge)

      GHPRDetailsComponentFactory.create(project,
                                         scope,
                                         reviewDetailsVm, reviewStatusVm, reviewFlowVm, commitsVm,
                                         dataProvider,
                                         dataContext.repositoryDataService, dataContext.securityService, dataContext.avatarIconsProvider,
                                         branchesModel,
                                         createCommitFilesBrowserComponent(scope, commitsVm))
    }.apply {
      isOpaque = true
      background = UIUtil.getListBackground()
      reloadDetailsAction.registerCustomShortcutSet(this, uiDisposable)
    }
  }

  private fun createCommitFilesBrowserComponent(scope: CoroutineScope, commitsVm: GHPRCommitsViewModel): JComponent {
    return GHLoadingPanelFactory(
      changesLoadingModel,
      null,
      GithubBundle.message("cannot.load.changes"),
      changesLoadingErrorHandler
    ).withContentListener {
      val tree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
      diffBridge.filesTree = tree
      diffBridge.commitsTree = tree
      diffBridge.activeTree = GHPRDiffController.ActiveTree.FILES
      tree?.showPullRequestProgress(uiDisposable, repository, dataProvider.reviewData, dataProvider.viewedStateData, diffBridge)
    }.createWithUpdatesStripe(uiDisposable) { parent, model ->
      val getCustomData = { tree: ChangesTree, dataId: String ->
        if (GHPRActionKeys.PULL_REQUEST_FILES.`is`(dataId)) tree.getPullRequestFiles()
        else null
      }

      val commitChangesModel = createCommitChangesModel(scope, model, commitsVm)
      val tree = createChangesTree(
        parent,
        commitChangesModel,
        GithubBundle.message("pull.request.does.not.contain.changes"),
        getCustomData
      )
      val controller = Controller(tree, model, commitsVm)
      ClientProperty.put(tree, GHPRCommitBrowserComponentController.KEY, controller)

      val scrollPane = ScrollPaneFactory.createScrollPane(tree, true)
      ScrollableContentBorder.setup(scrollPane, Side.TOP_AND_BOTTOM, parent)
      return@createWithUpdatesStripe scrollPane
    }
  }

  private fun createCommitChangesModel(
    scope: CoroutineScope,
    changesProviderModel: SingleValueModel<GitBranchComparisonResult>,
    commitsAndFilesVm: GHPRCommitsViewModel
  ): SingleValueModel<Collection<Change>> {
    val changesState = MutableStateFlow(changesProviderModel.value)
    changesProviderModel.addAndInvokeListener {
      changesState.value = it
    }
    val selectedChanges = combine(changesState, commitsAndFilesVm.selectedCommit) { changes, commit ->
      if (commit == null) {
        changes.changes
      }
      else {
        changes.changesByCommits[commit.oid].orEmpty()
      }
    }
    val commitChangesModel: SingleValueModel<Collection<Change>> = SingleValueModel(emptyList())
    scope.launch(start = CoroutineStart.UNDISPATCHED) {
      selectedChanges.collect {
        commitChangesModel.value = it
      }
    }
    return commitChangesModel
  }

  private fun createChangesTree(
    parentPanel: JPanel,
    model: SingleValueModel<Collection<Change>>,
    emptyTextText: @Nls String,
    getCustomData: ChangesTree.(String) -> Any? = { null }
  ): AsyncChangesTree {
    val tree = CodeReviewChangesTreeFactory(project, model).create(emptyTextText)

    val diffPreviewController = createAndSetupDiffPreview(tree, diffRequestProducer.changeProducerFactory, dataProvider,
                                                          dataContext.filesManager)

    tree.installPopupHandler(actionManager.getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    DataManager.registerDataProvider(parentPanel) { dataId ->
      when {
        EDITOR_TAB_DIFF_PREVIEW.`is`(dataId) -> diffPreviewController.activePreview
        tree.isShowing -> tree.getCustomData(dataId) ?: tree.getData(dataId)
        else -> null
      }
    }

    return tree
  }

  companion object {
    private fun ChangesTree.selectChange(toSelect: Change) {
      val rowInTree = findRowContainingChange(root, toSelect)
      if (rowInTree == -1) return
      setSelectionRow(rowInTree)
      TreeUtil.showRowCentered(this, rowInTree, false)
    }

    private fun ChangesTree.findRowContainingChange(root: TreeNode, toSelect: Change): Int {
      val targetNode = TreeUtil.treeNodeTraverser(root).traverse(TreeTraversal.POST_ORDER_DFS).find { node ->
        node is DefaultMutableTreeNode && node.userObject?.let {
          it is Change &&
          it == toSelect &&
          it.beforeRevision == toSelect.beforeRevision &&
          it.afterRevision == toSelect.afterRevision
        } ?: false
      }
      return if (targetNode != null) TreeUtil.getRowForNode(this, targetNode as DefaultMutableTreeNode) else -1
    }
  }
}

private fun ChangesTree.getPullRequestFiles(): Iterable<FilePath> =
  VcsTreeModelData.selected(this)
    .iterateUserObjects(Change::class.java)
    .map { ChangesUtil.getFilePath(it) }
