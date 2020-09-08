// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.toolwindow

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.ui.*
import com.intellij.ui.tabs.JBTabs
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.TabsListener
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.containers.TreeTraversal
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.codereview.ReturnToListComponent
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.plugins.github.api.data.GHCommit
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.i18n.GithubBundle
import org.jetbrains.plugins.github.pullrequest.GHPRDiffController
import org.jetbrains.plugins.github.pullrequest.action.GHPRActionKeys
import org.jetbrains.plugins.github.pullrequest.data.GHPRChangesProvider
import org.jetbrains.plugins.github.pullrequest.data.GHPRDataContext
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.ui.GHCompletableFutureLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingErrorHandlerImpl
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingModel
import org.jetbrains.plugins.github.pullrequest.ui.GHLoadingPanelFactory
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelper
import org.jetbrains.plugins.github.pullrequest.ui.changes.GHPRChangesDiffHelperImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRBranchesModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.details.GHPRDetailsModelImpl
import org.jetbrains.plugins.github.pullrequest.ui.toolwindow.GHPRCommitsBrowserComponent.COMMITS_LIST_KEY
import org.jetbrains.plugins.github.ui.HtmlInfoPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeNode
import kotlin.properties.Delegates.observable

internal class GHPRViewComponentFactory(private val actionManager: ActionManager,
                                        private val project: Project,
                                        private val dataContext: GHPRDataContext,
                                        private val viewController: GHPRToolWindowTabComponentController,
                                        pullRequest: GHPRIdentifier,
                                        private val disposable: Disposable) {
  private val dataProvider = dataContext.dataProviderRepository.getDataProvider(pullRequest, disposable)

  private val diffHelper = GHPRChangesDiffHelperImpl(project, dataProvider,
                                                     dataContext.avatarIconsProviderFactory,
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

  private val detailsLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
    dataProvider.detailsData.reloadDetails()
  }
  private val changesLoadingErrorHandler = GHLoadingErrorHandlerImpl(project, dataContext.securityService.account) {
    dataProvider.changesData.reloadChanges()
  }

  private val uiDisposable = Disposer.newDisposable().also {
    Disposer.register(disposable, it)
  }

  fun create(): JComponent {
    val infoTabInfo = TabInfo(createInfoComponent()).apply {
      text = GithubBundle.message("pull.request.info")
      sideComponent = createReturnToListSideComponent()
    }
    val commitsTabInfo = TabInfo(createCommitsComponent()).apply {
      text = GithubBundle.message("pull.request.commits")
      sideComponent = createReturnToListSideComponent()
    }

    val commitsTabTitleListener = object : GHLoadingModel.StateChangeListener {
      override fun onLoadingCompleted() {
        val commits = if (commitsLoadingModel.resultAvailable) commitsLoadingModel.result!! else null
        val commitsCount = commits?.size
        commitsTabInfo.text = if (commitsCount == null) GithubBundle.message("pull.request.commits")
        else GithubBundle.message("pull.request.commits.count", commitsCount)
      }
    }
    commitsLoadingModel.addStateChangeListener(commitsTabTitleListener)
    commitsTabTitleListener.onLoadingCompleted()

    return object : SingleHeightTabs(project, uiDisposable) {
      override fun adjust(each: TabInfo?) {}
    }.apply {
      setDataProvider { dataId ->
        when {
          GHPRActionKeys.GIT_REPOSITORY.`is`(dataId) -> dataContext.gitRemoteCoordinates.repository
          GHPRActionKeys.PULL_REQUEST_DATA_PROVIDER.`is`(dataId) -> this@GHPRViewComponentFactory.dataProvider
          GHPRChangesDiffHelper.DATA_KEY.`is`(dataId) -> diffHelper
          else -> null
        }
      }
      addTab(infoTabInfo)
      addTab(commitsTabInfo)
    }.also {
      val controller = Controller(it, infoTabInfo, commitsTabInfo)
      UIUtil.putClientProperty(it, GHPRViewComponentController.KEY, controller)
    }
  }

  private inner class Controller(private val tabs: JBTabs,
                                 private val infoTab: TabInfo,
                                 private val commitsTab: TabInfo) : GHPRViewComponentController {
    private val selectedChangesUpdater = SelectedChangesUpdater(dataProvider.diffController)

    init {
      val listener = object : TabsListener {
        private val propertyListener = PropertyChangeListener { evt ->
          if (evt.propertyName == CHANGES_TREE_KEY.toString()) {
            selectedChangesUpdater.tree = evt.newValue as ChangesTree?
          }
        }

        override fun selectionChanged(oldSelection: TabInfo?, newSelection: TabInfo?) {
          oldSelection?.component?.removePropertyChangeListener(propertyListener)
          newSelection?.component?.addPropertyChangeListener(propertyListener)

          val tree = newSelection?.component?.let { ComponentUtil.getClientProperty(it, CHANGES_TREE_KEY) }
          propertyListener.propertyChange(PropertyChangeEvent(this, CHANGES_TREE_KEY.toString(), null, tree))
        }
      }
      tabs.addListener(listener)
      listener.selectionChanged(null, tabs.selectedInfo)
    }

    override fun selectCommit(oid: String) {
      tabs.select(commitsTab, false)
      val list = ComponentUtil.getClientProperty(commitsTab.component, COMMITS_LIST_KEY) ?: return
      for (i in 0 until list.model.size) {
        val commit = list.model.getElementAt(i)
        if (commit.oid == oid || commit.abbreviatedOid == oid) {
          list.selectedIndex = i
          break
        }
      }
      GithubUIUtil.focusPanel(list)
    }

    override fun selectChange(oid: String?, filePath: String) {
      tabs.select(infoTab, false)
      val tree = ComponentUtil.getClientProperty(infoTab.component, CHANGES_TREE_KEY) ?: return
      GithubUIUtil.focusPanel(tree)

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

  private fun createReturnToListSideComponent(): JComponent {
    return ReturnToListComponent.createReturnToListSideComponent(GithubBundle.message("pull.request.back.to.list")) {
      viewController.viewList()
    }
  }

  private fun createInfoComponent(): JComponent {
    val splitter = OnePixelSplitter(true, "Github.PullRequest.Info.Component", 0.33f).apply {
      isOpaque = true
      background = UIUtil.getListBackground()
    }

    val detailsLoadingPanel = GHLoadingPanelFactory(detailsLoadingModel,
                                                    null, GithubBundle.message("cannot.load.details"),
                                                    detailsLoadingErrorHandler).createWithUpdatesStripe(uiDisposable) { _, model ->
      val detailsModel = GHPRDetailsModelImpl(model,
                                              dataContext.securityService,
                                              dataContext.repositoryDataService,
                                              dataProvider.detailsData)

      val branchesModel = GHPRBranchesModelImpl(model,
                                                dataProvider.detailsData,
                                                dataContext.gitRemoteCoordinates.repository,
                                                disposable)
      GHPRDetailsComponent.create(detailsModel, branchesModel, dataContext.avatarIconsProviderFactory)
    }.also {
      reloadDetailsAction.registerCustomShortcutSet(it, uiDisposable)
    }

    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel, null,
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        val changesTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
        ComponentUtil.putClientProperty(splitter, CHANGES_TREE_KEY, changesTree)
      }
      .createWithUpdatesStripe(uiDisposable) { parent, model ->
        createChangesTree(parent, model.map { it.changes }, GithubBundle.message("pull.request.does.not.contain.changes"))
      }.apply {
        border = IdeBorderFactory.createBorder(SideBorder.TOP)
      }
    val toolbar = createChangesBrowserToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return splitter.apply {
      firstComponent = detailsLoadingPanel
      secondComponent = changesBrowser
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
        val browser = GHPRCommitsBrowserComponent.create(model, commitSelectionListener)
        val list = UIUtil.getClientProperty(browser, COMMITS_LIST_KEY)
        UIUtil.putClientProperty(splitter, COMMITS_LIST_KEY, list)
        browser
      }

    val changesLoadingPanel = GHLoadingPanelFactory(changesLoadingModel,
                                                    GithubBundle.message("pull.request.select.commit.to.view.changes"),
                                                    GithubBundle.message("cannot.load.changes"),
                                                    changesLoadingErrorHandler)
      .withContentListener {
        val changesTree = UIUtil.findComponentOfType(it, ChangesTree::class.java)
        ComponentUtil.putClientProperty(splitter, CHANGES_TREE_KEY, changesTree)
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
    val toolbar = createChangesBrowserToolbar(changesLoadingPanel)
    val changesBrowser = BorderLayoutPanel().andTransparent()
      .addToTop(toolbar)
      .addToCenter(changesLoadingPanel)

    return splitter.apply {
      firstComponent = commitsLoadingPanel
      secondComponent = changesBrowser
    }
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
    val model = SingleValueModel(changesModel.value.changesByCommits[commitSelectionListener.currentCommit].orEmpty())
    fun update() {
      val commit = commitSelectionListener.currentCommit
      model.value = changesModel.value.changesByCommits[commit].orEmpty()
    }
    commitSelectionListener.delegate = ::update
    changesModel.addAndInvokeValueChangedListener(::update)
    return model
  }

  private fun createChangesTree(parentPanel: JPanel, model: SingleValueModel<List<Change>>, emptyTextText: String): JComponent {

    val tree = object : ChangesTree(project, false, false) {
      override fun rebuildTree() {
        updateTreeModel(TreeModelBuilder(project, grouping).setChanges(model.value, null).build())
        if (isSelectionEmpty && !isEmpty) TreeUtil.selectFirstNode(this)
      }

      override fun getData(dataId: String) = super.getData(dataId) ?: VcsTreeModelData.getData(project, this, dataId)

    }.apply {
      emptyText.text = emptyTextText
    }.also {
      it.doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(it, e)) return@Processor false
        viewController.openPullRequestDiff(dataProvider.id, true)
        true
      }
      it.enterKeyHandler = Processor {
        viewController.openPullRequestDiff(dataProvider.id, true)
        true
      }

      UIUtil.putClientProperty(it, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
      SelectionSaver.installOn(it)
      it.addFocusListener(object : FocusAdapter() {
        override fun focusGained(e: FocusEvent?) {
          if (it.isSelectionEmpty && !it.isEmpty) TreeUtil.selectFirstNode(it)
        }
      })
    }

    model.addAndInvokeValueChangedListener(tree::rebuildTree)

    reloadChangesAction.registerCustomShortcutSet(tree, null)
    tree.installPopupHandler(actionManager.getAction("Github.PullRequest.Changes.Popup") as ActionGroup)

    DataManager.registerDataProvider(parentPanel) {
      if (tree.isShowing) tree.getData(it) else null
    }
    return ScrollPaneFactory.createScrollPane(tree, true)
  }

  private class CommitSelectionListener : (GHCommit?) -> Unit {
    var currentCommit: GHCommit? = null
    var delegate: (() -> Unit)? = null

    override fun invoke(commit: GHCommit?) {
      currentCommit = commit
      delegate?.invoke()
    }
  }

  private fun createChangesBrowserToolbar(target: JComponent)
    : TreeActionsToolbarPanel {

    val changesToolbarActionGroup = actionManager.getAction("Github.PullRequest.Changes.Toolbar") as ActionGroup
    val changesToolbar = actionManager.createActionToolbar("ChangesBrowser", changesToolbarActionGroup, true)
    val treeActionsGroup = DefaultActionGroup(actionManager.getAction(IdeActions.ACTION_EXPAND_ALL),
                                              actionManager.getAction(IdeActions.ACTION_COLLAPSE_ALL))
    return TreeActionsToolbarPanel(changesToolbar, treeActionsGroup, target)
  }

  private class SelectedChangesUpdater(diffController: GHPRDiffController) {
    private val selectionListener = TreeSelectionListener { _ ->
      val selection = tree?.let { VcsTreeModelData.getListSelectionOrAll(it).map { it as? Change } }
      // do not reset selection to zero
      if (selection != null && !selection.isEmpty) diffController.selection = selection
    }

    var tree: ChangesTree? by observable<ChangesTree?>(null) { _, oldValue, newValue ->
      oldValue?.removeTreeSelectionListener(selectionListener)
      newValue?.addTreeSelectionListener(selectionListener)
      selectionListener.valueChanged(null)
    }
  }

  companion object {
    private val CHANGES_TREE_KEY = Key.create<ChangesTree>("CHANGES_TREE")
  }
}
