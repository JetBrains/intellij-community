// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.collaboration.async.DisposingScope
import com.intellij.collaboration.ui.CollaborationToolsUIUtil
import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.ReturnToListComponent
import com.intellij.collaboration.ui.codereview.commits.CommitsBrowserComponentBuilder
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
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcsUtil.VcsUtil
import git4idea.repo.GitRepository
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestFileViewedState
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRCombinedDiffPreviewBase.Companion.createAndSetupDiffPreview
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys.PULL_REQUEST_FILES
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.*
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.details.model.impl.*
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode

internal class GHPRViewComponentFactory(private val actionManager: ActionManager,
                                        private val project: Project,
                                        private val dataContext: GHPRDataContext,
                                        private val viewController: GHPRToolWindowTabComponentController,
                                        pullRequest: GHPRIdentifier,
                                        private val disposable: Disposable) {
  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

  private val detailsLoadingModel = GHCompletableFutureLoadingModel<GHPullRequest>(disposable)
  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(disposable)
  private val changesLoadingModel = GHCompletableFutureLoadingModel<GHPRChangesProvider>(disposable)
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
  private val reloadChangesAction = actionManager.getAction("Github.PullRequest.Changes.Reload")

  private val detailsLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }
  private val changesLoadingErrorHandler = GHApiLoadingErrorHandler(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  private val repository: GitRepository get() = dataContext.repositoryDataService.remoteCoordinates.repository

  private val diffRequestProducer: GHPRDiffRequestChainProducer =
    object : GHPRDiffRequestChainProducer(project,
                                          dataProvider, dataContext.avatarIconsProvider,
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

  fun create(): JComponent {
    val returnToListActionComponent = ReturnToListComponent.createReturnToListSideComponent(
      GithubBundle.message("pull.request.back.to.list"),
      onClick = { viewController.viewList() }
    )
    val infoComponent = createInfoComponent()

    return JPanel(BorderLayout()).apply {
      isOpaque = false
      add(returnToListActionComponent, BorderLayout.NORTH)
      add(infoComponent, BorderLayout.CENTER)
    }.apply {
      DataManager.registerDataProvider(this) { dataId ->
        when {
          GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> repository
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
          DiffRequestChainProducer.DATA_KEY.`is`(dataId) -> diffRequestProducer
          else -> null
        }
      }
    }
  }

  private inner class Controller(private val tabs: JBTabs,
                                 private val filesComponent: JComponent,
                                 private val commitsComponent: JComponent) : GHPRViewComponentController {

    override fun selectCommit(oid: String) {
      tabs.findInfo(commitsComponent)?.let {
        tabs.select(it, false)
      }

      val list = findCommitsList(commitsComponent) ?: return
      for (i in 0 until list.model.size) {
        val commit = list.model.getElementAt(i)
        if (commit.id.asString().startsWith(oid)) {
          list.selectedIndex = i
          break
        }
      }
      CollaborationToolsUIUtil.focusPanel(list)
    }

    private fun findCommitsList(parent: JComponent): JList<VcsCommitMetadata>? {
      UIUtil.getClientProperty(parent, CommitsBrowserComponentBuilder.COMMITS_LIST_KEY)?.run {
        return this
      }

      for (component in parent.components) {
        if (component is JComponent) {
          findCommitsList(component)?.run {
            return this
          }
        }
      }
      return null
    }

    override fun selectChange(oid: String?, filePath: String) {
      tabs.findInfo(filesComponent)?.let {
        tabs.select(it, false)
      }
      val tree = UIUtil.findComponentOfType(filesComponent, ChangesTree::class.java) ?: return
      CollaborationToolsUIUtil.focusPanel(tree)

      if (oid == null || !changesLoadingModel.resultAvailable) {
        tree.selectFile(VcsUtil.getFilePath(filePath, false))
      }
      else {
        val change = changesLoadingModel.result!!.findCumulativeChange(oid, filePath)
        if (change == null) {
          tree.selectFile(VcsUtil.getFilePath(filePath, false))
        }
        else {
          tree.selectChange(change)
        }
      }
    }
  }

  private fun createInfoComponent(): JComponent {
    val detailsLoadingPanel = GHLoadingPanelFactory(detailsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.details"),
                                                    detailsLoadingErrorHandler).createWithUpdatesStripe(uiDisposable) { _, model ->
      val branchesModel = GHPRBranchesModelImpl(model,
                                                dataProvider.detailsData,
                                                repository,
                                                disposable)

      val detailsModel = GHPRDetailsModelImpl(model)

      val metadataModel = GHPRMetadataModelImpl(model,
                                                dataContext.securityService,
                                                dataContext.repositoryDataService,
                                                dataProvider.detailsData)

      val stateModel = GHPRStateModelImpl(project, dataProvider.stateData, dataProvider.changesData, model, disposable)

      val scope = DisposingScope(disposable)
      val reviewDetailsVm = GHPRDetailsViewModelImpl(scope, detailsModel, stateModel)
      val reviewFlowVm = GHPRReviewFlowViewModelImpl(scope,
                                                     metadataModel,
                                                     dataContext.securityService,
                                                     dataProvider.detailsData,
                                                     dataProvider.reviewData,
                                                     disposable)

      GHPRDetailsComponentFactory.create(project,
                                         scope,
                                         reviewDetailsVm, reviewFlowVm,
                                         dataProvider,
                                         dataContext.repositoryDataService, dataContext.securityService, dataContext.avatarIconsProvider,
                                         branchesModel, metadataModel, stateModel)
    }.also {
      reloadDetailsAction.registerCustomShortcutSet(it, uiDisposable)
    }
    return Wrapper(detailsLoadingPanel).apply {
      isOpaque = true
      background = UIUtil.getListBackground()
    }
  }

  private fun createFilesComponent(): JComponent {
    val panel = BorderLayoutPanel().withBackground(UIUtil.getListBackground())
    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel, null,
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        val tree = UIUtil.findComponentOfType(it, ChangesTree::class.java)

        diffBridge.filesTree = tree
        tree?.showPullRequestProgress(uiDisposable, repository, dataProvider.reviewData, dataProvider.viewedStateData)
      }
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        val getCustomData = { tree: ChangesTree, dataId: String ->
          if (PULL_REQUEST_FILES.`is`(dataId)) tree.getPullRequestFiles()
          else null
        }

        createChangesTree(parent, model.map { it.changes }, GithubBundle.message("pull.request.does.not.contain.changes"), getCustomData)
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = GHPRChangesTreeFactory.createTreeToolbar(actionManager, changesLoadingPanel)
    return panel.addToTop(toolbar).addToCenter(changesLoadingPanel)
  }

  private fun createCommitChangesModel(changesModel: SingleValueModel<GHPRChangesProvider>,
                                       commitSelectionListener: CommitSelectionListener): SingleValueModel<List<Change>> {
    val model = SingleValueModel(changesModel.value.changesByCommits[commitSelectionListener.currentCommit?.id?.asString()].orEmpty())
    fun update() {
      val commit = commitSelectionListener.currentCommit
      model.value = changesModel.value.changesByCommits[commit?.id?.asString()].orEmpty()
    }
    commitSelectionListener.delegate = ::update
    changesModel.addAndInvokeListener { update() }
    return model
  }

  private fun createChangesTree(
    parentPanel: JPanel,
    model: SingleValueModel<List<Change>>,
    emptyTextText: String,
    getCustomData: ChangesTree.(String) -> Any? = { null }
  ): JComponent {
    val tree = GHPRChangesTreeFactory(project, model).create(emptyTextText)

    val diffPreviewController = createAndSetupDiffPreview(tree, diffRequestProducer.changeProducerFactory, dataProvider,
                                                          dataContext.filesManager)

    reloadChangesAction.registerCustomShortcutSet(tree, null)
    tree.installPopupHandler(actionManager.getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    DataManager.registerDataProvider(parentPanel) { dataId ->
      when {
        EDITOR_TAB_DIFF_PREVIEW.`is`(dataId) -> diffPreviewController.activePreview
        tree.isShowing -> tree.getCustomData(dataId) ?: tree.getData(dataId)
        else -> null
      }
    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  private class CommitSelectionListener : (VcsCommitMetadata?) -> Unit {
    var currentCommit: VcsCommitMetadata? = null
    var delegate: (() -> Unit)? = null

    override fun invoke(commit: VcsCommitMetadata?) {
      currentCommit = commit
      delegate?.invoke()
    }
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
