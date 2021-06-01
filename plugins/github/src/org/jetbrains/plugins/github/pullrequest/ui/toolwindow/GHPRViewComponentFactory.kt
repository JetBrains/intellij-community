// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.DiffPreview
import com.intellij.openapi.vcs.changes.EditorTabDiffPreviewManager.Companion.EDITOR_TAB_DIFF_PREVIEW
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.tabs.JBTabs
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHApiLoadingErrorHandler
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesTreeFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRCommitsBrowserComponentFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRDiffRequestChainProducer
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRBranchesModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRMetadataModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRStateModelImpl
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import org.jetbrains.plugins.github.ui.util.GHUIUtil
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.DiffRequestChainProducer
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

  private val diffRequestProducer = GHPRDiffRequestChainProducer(project, dataProvider,
                                                                 dataContext.avatarIconsProvider,
                                                                 dataContext.securityService.currentUser)

  private val detailsLoadingModel = GHCompletableFutureLoadingModel<GHPullRequest>(disposable)
  private val commitsLoadingModel = GHCompletableFutureLoadingModel<List<GHCommit>>(disposable)
  private val changesLoadingModel = GHCompletableFutureLoadingModel<GHPRChangesProvider>(disposable)

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

  private val diffBridge = GHPRDiffController(dataProvider.diffRequestModel, diffRequestProducer)

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(): JComponent {
    val infoComponent = createInfoComponent()

    val filesComponent = createFilesComponent()
    val filesCountModel = createFilesCountModel()

    val commitsComponent = createCommitsComponent()
    val commitsCountModel = createCommitsCountModel()

    val tabs = GHPRViewTabsFactory(project, viewController::viewList, uiDisposable)
      .create(infoComponent,
              diffBridge,
              filesComponent, filesCountModel,
              commitsComponent, commitsCountModel)
      .apply {
        setDataProvider { dataId ->
          when {
            GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> dataContext.repositoryDataService.remoteCoordinates.repository
            GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
            DiffRequestChainProducer.DATA_KEY.`is`(dataId) -> diffRequestProducer
            else -> null
          }
        }
      }
    val controller = Controller(tabs, filesComponent, commitsComponent)
    return tabs.component.also {
      UIUtil.putClientProperty(it, GHPRViewComponentController.KEY, controller)
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
      GHUIUtil.focusPanel(list)
    }

    private fun findCommitsList(parent: JComponent): JList<VcsCommitMetadata>? {
      UIUtil.getClientProperty(parent, GHPRCommitsBrowserComponentFactory.COMMITS_LIST_KEY)?.run {
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
      GHUIUtil.focusPanel(tree)

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
                                                dataContext.repositoryDataService.repositoryMapping.gitRemote.repository,
                                                disposable)

      val detailsModel = GHPRDetailsModelImpl(model)

      val metadataModel = GHPRMetadataModelImpl(model,
                                                dataContext.securityService,
                                                dataContext.repositoryDataService,
                                                dataProvider.detailsData)

      val stateModel = GHPRStateModelImpl(project, dataProvider.stateData, dataProvider.changesData, model, disposable)

      GHPRDetailsComponent.create(dataContext.securityService,
                                  dataContext.avatarIconsProvider,
                                  branchesModel, detailsModel, metadataModel, stateModel)
    }.also {
      reloadDetailsAction.registerCustomShortcutSet(it, uiDisposable)
    }
    return Wrapper(detailsLoadingPanel).apply {
      isOpaque = true
      background = UIUtil.getListBackground()
    }
  }

  private fun createCommitsComponent(): JComponent {
    val splitter = OnePixelSplitter(true, "Github.PullRequest.Commits.Component", 0.4f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()
    }.also {
      reloadChangesAction.registerCustomShortcutSet(it, uiDisposable)
    }

    val commitSelectionListener = CommitSelectionListener()

    val commitsLoadingPanel = GHLoadingPanelFactory(commitsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.commits"),
                                                    changesLoadingErrorHandler)
      .createWithUpdatesStripe(uiDisposable) { _, model ->
        GHPRCommitsBrowserComponentFactory(project).create(model.map { list ->
          val logObjectsFactory = project.service<VcsLogObjectsFactory>()
          list.map { commit ->
            logObjectsFactory.createCommitMetadata(
              HashImpl.build(commit.oid),
              commit.parents.map { HashImpl.build(it.oid) },
              commit.committer?.date?.time ?: 0L,
              dataContext.repositoryDataService.remoteCoordinates.repository.root,
              commit.messageHeadline,
              commit.author?.name ?: "unknown user",
              commit.author?.email ?: "",
              commit.messageHeadlineHTML + if (commit.messageBodyHTML.isEmpty()) "" else "\n\n${commit.messageBodyHTML}",
              commit.committer?.name ?: "unknown user",
              commit.committer?.email ?: "",
              commit.author?.date?.time ?: 0L
            )
          }
        }, commitSelectionListener)
      }

    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel,
                                                    GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        diffBridge.commitsTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
      }
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        val reviewUnsupportedWarning = createReviewUnsupportedPlaque(model)
        JBUI.Panels.simplePanel(createChangesTree(parent, createCommitChangesModel(model, commitSelectionListener),
                                                  GithubBundle.message("pull.request.commit.does.not.contain.changes")))
          .addToTop(reviewUnsupportedWarning)
          .andTransparent()
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = GHPRChangesTreeFactory.createTreeToolbar(actionManager, changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return splitter.apply {
      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }
  }

  private fun createCommitsCountModel(): SingleValueModel<Int?> {
    val model = SingleValueModel<Int?>(null)
    val loadingListener = object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val commits = if (commitsLoadingModel.resultAvailable) commitsLoadingModel.result!! else null
        model.value = commits?.size
      }
    }
    commitsLoadingModel.addStateChangeListener(loadingListener)
    loadingListener.onLoadingCompleted()
    return model
  }

  private fun createFilesComponent(): JComponent {
    val panel = BorderLayoutPanel().withBackground(UIUtil.getListBackground())
    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel, null,
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        diffBridge.filesTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
      }
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        createChangesTree(parent, model.map { it.changes }, GithubBundle.message("pull.request.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = GHPRChangesTreeFactory.createTreeToolbar(actionManager, changesLoadingPanel)
    return panel.addToTop(toolbar).addToCenter(changesLoadingPanel)
  }

  private fun createFilesCountModel(): SingleValueModel<Int?> {
    val model = SingleValueModel<Int?>(null)
    val loadingListener = object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val changesProvider = if (changesLoadingModel.resultAvailable) changesLoadingModel.result!! else null
        model.value = changesProvider?.changes?.size
      }
    }
    changesLoadingModel.addStateChangeListener(loadingListener)
    loadingListener.onLoadingCompleted()
    return model
  }

  private fun createReviewUnsupportedPlaque(model: SingleValueModel<GHPRChangesProvider>) = HtmlInfoPanel().apply {
    setInfo(GithubBundle.message("pull.request.review.not.supported.non.linear"), HtmlInfoPanel.Severity.WARNING)
    border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)

    model.addAndInvokeValueChangedListener {
      isVisible = !model.value.linearHistory
    }
  }

  private fun createCommitChangesModel(changesModel: SingleValueModel<GHPRChangesProvider>,
                                       commitSelectionListener: CommitSelectionListener): SingleValueModel<List<Change>> {
    val model = SingleValueModel(changesModel.value.changesByCommits[commitSelectionListener.currentCommit?.id?.asString()].orEmpty())
    fun update() {
      val commit = commitSelectionListener.currentCommit
      model.value = changesModel.value.changesByCommits[commit?.id?.asString()].orEmpty()
    }
    commitSelectionListener.delegate = ::update
    changesModel.addAndInvokeValueChangedListener(::update)
    return model
  }

  private fun createChangesTree(parentPanel: JPanel,
                                model: SingleValueModel<List<Change>>,
                                emptyTextText: String): JComponent {
    val editorDiffPreview = object : DiffPreview {
      override fun setPreviewVisible(isPreviewVisible: Boolean, focus: Boolean) {
        if (isPreviewVisible) {
          viewController.openPullRequestDiff(dataProvider.id, focus)
        }
      }
    }

    val tree = GHPRChangesTreeFactory(project, model).create(emptyTextText).also {
      it.doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(it, e)) return@Processor false
        editorDiffPreview.setPreviewVisible(true, true)
        true
      }
      it.enterKeyHandler = Processor {
        editorDiffPreview.setPreviewVisible(true, true)
        true
      }
    }

    reloadChangesAction.registerCustomShortcutSet(tree, null)
    tree.installPopupHandler(actionManager.getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    DataManager.registerDataProvider(parentPanel) {
      if (EDITOR_TAB_DIFF_PREVIEW.`is`(it)) return@registerDataProvider editorDiffPreview

      if (tree.isShowing) tree.getData(it) else null
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
