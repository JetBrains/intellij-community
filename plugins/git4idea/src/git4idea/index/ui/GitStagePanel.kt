// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ActionUtil.performActionDumbAwareWithCallbacks
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ChangesViewManager.createTextStatusFactory
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EventDispatcher
import com.intellij.util.OpenSourceUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.ProportionKey
import com.intellij.util.ui.ThreeStateCheckBox
import com.intellij.util.ui.TwoKeySplitter
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.CommitStatusPanel
import com.intellij.vcs.commit.CommitWorkflowListener
import com.intellij.vcs.commit.EditedCommitNode
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.ProgressStripe
import git4idea.GitVcs
import git4idea.conflicts.GitConflictsUtil.canShowMergeWindow
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.conflicts.GitMergeHandler
import git4idea.i18n.GitBundle.message
import git4idea.index.GitStageCommitWorkflow
import git4idea.index.GitStageCommitWorkflowHandler
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.actions.GitAddOperation
import git4idea.index.actions.GitResetOperation
import git4idea.index.actions.StagingAreaOperation
import git4idea.index.actions.performStageOperation
import git4idea.merge.GitDefaultMergeDialogCustomizer
import git4idea.repo.GitConflict
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.status.GitRefreshListener
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.util.*
import java.util.stream.Collectors
import javax.swing.JPanel

internal class GitStagePanel(private val tracker: GitStageTracker,
                             isVertical: Boolean,
                             isEditorDiffPreview: Boolean,
                             disposableParent: Disposable,
                             private val activate: () -> Unit) :
  JPanel(BorderLayout()), DataProvider, Disposable {
  private val project = tracker.project
  private val disposableFlag = Disposer.newCheckedDisposable()

  private val _tree: MyChangesTree
  val tree: ChangesTree get() = _tree
  private val treeMessageSplitter: Splitter
  private val commitPanel: GitStageCommitPanel
  private val commitWorkflowHandler: GitStageCommitWorkflowHandler
  private val progressStripe: ProgressStripe
  private val commitDiffSplitter: OnePixelSplitter
  private val toolbar: ActionToolbar
  private val changesStatusPanel: Wrapper

  private var diffPreviewProcessor: GitStageDiffPreview? = null
  private var editorTabPreview: EditorTabPreview? = null

  private val state: GitStageTracker.State
    get() = tracker.state

  private var hasPendingUpdates = false

  internal val commitMessage get() = commitPanel.commitMessage

  init {
    _tree = MyChangesTree(project)

    commitPanel = GitStageCommitPanel(project)
    commitPanel.commitActionsPanel.isCommitButtonDefault = {
      !commitPanel.commitProgressUi.isDumbMode &&
      IdeFocusManager.getInstance(project).getFocusedDescendantFor(this) != null
    }
    commitPanel.commitActionsPanel.setupShortcuts(this, this)
    commitPanel.addEditedCommitListener(_tree::editedCommitChanged, this)
    commitPanel.setIncludedRoots(_tree.getIncludedRoots())
    _tree.addIncludedRootsListener(object : IncludedRootsListener {
      override fun includedRootsChanged() {
        commitPanel.setIncludedRoots(_tree.getIncludedRoots())
      }
    }, this)
    commitWorkflowHandler = GitStageCommitWorkflowHandler(GitStageCommitWorkflow(project), commitPanel)
    Disposer.register(this, commitPanel)

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stage.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    toolbar = ActionManager.getInstance().createActionToolbar(GIT_STAGE_PANEL_PLACE, toolbarGroup, true)
    toolbar.targetComponent = tree

    PopupHandler.installPopupMenu(tree, "Git.Stage.Tree.Menu", "Git.Stage.Tree.Menu")

    val statusPanel = CommitStatusPanel(commitPanel).apply {
      border = empty(0, 1, 0, 6)
      background = tree.background

      addToLeft(commitPanel.toolbar.component)
    }
    val treePanel = simplePanel(createScrollPane(tree, SideBorder.TOP)).addToBottom(statusPanel)
    progressStripe = ProgressStripe(treePanel, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    val treePanelWithToolbar = JPanel(BorderLayout())
    treePanelWithToolbar.add(toolbar.component, BorderLayout.NORTH)
    treePanelWithToolbar.add(progressStripe, BorderLayout.CENTER)

    treeMessageSplitter = TwoKeySplitter(true, ProportionKey("git.stage.tree.message.splitter", 0.7f,
                                                             "git.stage.tree.message.splitter.horizontal", 0.5f))
    treeMessageSplitter.firstComponent = treePanelWithToolbar
    treeMessageSplitter.secondComponent = commitPanel

    changesStatusPanel = Wrapper()
    changesStatusPanel.minimumSize = JBUI.emptySize()

    commitDiffSplitter = OnePixelSplitter("git.stage.commit.diff.splitter", 0.5f)
    commitDiffSplitter.firstComponent = treeMessageSplitter
    add(commitDiffSplitter, BorderLayout.CENTER)
    add(changesStatusPanel, BorderLayout.SOUTH)

    updateLayout(isVertical, isEditorDiffPreview, forceDiffPreview = true)

    tracker.addListener(MyGitStageTrackerListener(), this)
    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(GitRefreshListener.TOPIC, MyGitChangeProviderListener())
    busConnection.subscribe(ChangeListListener.TOPIC, MyChangeListListener())
    commitWorkflowHandler.workflow.addListener(MyCommitWorkflowListener(), this)

    if (isRefreshInProgress()) {
      tree.setEmptyText(message("stage.loading.status"))
      progressStripe.startLoadingImmediately()
    }
    updateChangesStatusPanel()

    Disposer.register(disposableParent, this)
    Disposer.register(this, disposableFlag)

    runInEdtAsync(disposableFlag) { update() }
  }

  private fun isRefreshInProgress(): Boolean {
    if (GitVcs.getInstance(project).changeProvider!!.isRefreshInProgress) return true
    return GitRepositoryManager.getInstance(project).repositories.any {
      it.untrackedFilesHolder.isInUpdateMode ||
      it.ignoredFilesHolder.isInUpdateMode()
    }
  }

  private fun updateChangesStatusPanel() {
    val manager = ChangeListManagerImpl.getInstanceImpl(project)
    val factory = manager.updateException?.let { createTextStatusFactory(VcsBundle.message("error.updating.changes", it.message), true) }
                  ?: manager.additionalUpdateInfo
    changesStatusPanel.setContent(factory?.create())
  }

  @RequiresEdt
  fun update() {
    if (commitWorkflowHandler.workflow.isExecuting) {
      hasPendingUpdates = true
      return
    }
    tree.rebuildTree()
    commitPanel.setTrackerState(state)
    commitWorkflowHandler.state = state
  }

  override fun getData(dataId: String): Any? {
    if (QuickActionProvider.KEY.`is`(dataId)) return toolbar
    if (EditorTabDiffPreviewManager.EDITOR_TAB_DIFF_PREVIEW.`is`(dataId)) return editorTabPreview
    return null
  }

  fun updateLayout(isVertical: Boolean, canUseEditorDiffPreview: Boolean, forceDiffPreview: Boolean = false) {
    val isEditorDiffPreview = canUseEditorDiffPreview || isVertical
    val isMessageSplitterVertical = isVertical || !isEditorDiffPreview
    if (treeMessageSplitter.orientation != isMessageSplitterVertical) {
      treeMessageSplitter.orientation = isMessageSplitterVertical
    }
    setDiffPreviewInEditor(isEditorDiffPreview, forceDiffPreview)
  }

  private fun setDiffPreviewInEditor(isInEditor: Boolean, force: Boolean = false) {
    if (disposableFlag.isDisposed) return
    if (!force && (isInEditor == (editorTabPreview != null))) return

    if (diffPreviewProcessor != null) Disposer.dispose(diffPreviewProcessor!!)
    diffPreviewProcessor = GitStageDiffPreview(project, _tree, tracker, isInEditor, this)
    diffPreviewProcessor!!.getToolbarWrapper().setVerticalSizeReferent(toolbar.component)

    if (isInEditor) {
      editorTabPreview = GitStageEditorDiffPreview(diffPreviewProcessor!!, tree, this, activate)
      commitDiffSplitter.secondComponent = null
    }
    else {
      editorTabPreview = null
      commitDiffSplitter.secondComponent = diffPreviewProcessor!!.component
    }
  }

  override fun dispose() {
  }

  private inner class MyChangesTree(project: Project) : GitStageTree(project, project.service<GitStageUiSettingsImpl>(),
                                                                     this@GitStagePanel) {
    override val state
      get() = this@GitStagePanel.state
    override val ignoredFilePaths
      get() = this@GitStagePanel.tracker.ignoredPaths
    override val operations: List<StagingAreaOperation> = listOf(GitAddOperation, GitResetOperation)

    private val includedRootsListeners = EventDispatcher.create(IncludedRootsListener::class.java)

    init {
      isShowCheckboxes = true

      setInclusionModel(GitStageRootInclusionModel(project, tracker, this@GitStagePanel))
      groupingSupport.addPropertyChangeListener(PropertyChangeListener {
        includedRootsListeners.multicaster.includedRootsChanged()
      })
      inclusionModel.addInclusionListener(object : InclusionListener {
        override fun inclusionChanged() {
          includedRootsListeners.multicaster.includedRootsChanged()
        }
      })
      tracker.addListener(object : GitStageTrackerListener {
        override fun update() {
          includedRootsListeners.multicaster.includedRootsChanged()
        }
      }, this@GitStagePanel)

      doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false

        val dataContext = DataManager.getInstance().getDataContext(this)

        val mergeAction = ActionManager.getInstance().getAction("Git.Stage.Merge")
        val event = AnActionEvent.createFromAnAction(mergeAction, e, ActionPlaces.UNKNOWN, dataContext)
        if (ActionUtil.lastUpdateAndCheckDumb(mergeAction, event, true)) {
          performActionDumbAwareWithCallbacks(mergeAction, event)
        }
        else {
          OpenSourceUtil.openSourcesFrom(dataContext, true)
        }
        true
      }
    }

    fun editedCommitChanged() {
      rebuildTree()

      commitPanel.editedCommit?.let {
        val node = TreeUtil.findNodeWithObject(root, it)
        node?.let { expandPath(TreeUtil.getPathFromRoot(node)) }
      }
    }

    override fun customizeTreeModel(builder: TreeModelBuilder) {
      super.customizeTreeModel(builder)

      commitPanel.editedCommit?.let {
        val commitNode = EditedCommitNode(it)
        builder.insertSubtreeRoot(commitNode)
        builder.insertChanges(it.commit.changes, commitNode)
      }
    }

    override fun performStageOperation(nodes: List<GitFileStatusNode>, operation: StagingAreaOperation) {
      performStageOperation(project, nodes, operation)
    }

    override fun getDndOperation(targetKind: NodeKind): StagingAreaOperation? {
      return when (targetKind) {
        NodeKind.STAGED -> GitAddOperation
        NodeKind.UNSTAGED -> GitResetOperation
        else -> null
      }
    }

    override fun showMergeDialog(conflictedFiles: List<VirtualFile>) {
      AbstractVcsHelper.getInstance(project).showMergeDialog(conflictedFiles)
    }

    override fun createHoverIcon(node: ChangesBrowserGitFileStatusNode): HoverIcon? {
      val conflict = node.conflict ?: return null
      val mergeHandler = createMergeHandler(project)
      if (!canShowMergeWindow(project, mergeHandler, conflict)) return null
      return GitStageMergeHoverIcon(mergeHandler, conflict)
    }

    fun getIncludedRoots(): Collection<VirtualFile> {
      if (!isInclusionEnabled()) return state.allRoots

      return inclusionModel.getInclusion().mapNotNull { (it as? GitRepository)?.root }
    }

    fun addIncludedRootsListener(listener: IncludedRootsListener, disposable: Disposable) {
      includedRootsListeners.addListener(listener, disposable)
    }

    private fun isInclusionEnabled(): Boolean {
      return state.rootStates.size > 1 && state.stagedRoots.size > 1 &&
             groupingSupport.isAvailable(REPOSITORY_GROUPING) &&
             groupingSupport[REPOSITORY_GROUPING]
    }

    override fun isInclusionEnabled(node: ChangesBrowserNode<*>): Boolean {
      return isInclusionEnabled() && node is RepositoryChangesBrowserNode && isUnderKind(node, NodeKind.STAGED)
    }

    override fun isInclusionVisible(node: ChangesBrowserNode<*>): Boolean = isInclusionEnabled(node)

    override fun getIncludableUserObjects(treeModelData: VcsTreeModelData): List<Any> {
      return treeModelData
        .rawNodesStream()
        .filter { node -> isIncludable(node) }
        .map { node -> node.userObject }
        .collect(Collectors.toList())
    }

    override fun getNodeStatus(node: ChangesBrowserNode<*>): ThreeStateCheckBox.State {
      return inclusionModel.getInclusionState(node.userObject)
    }

    private fun isUnderKind(node: ChangesBrowserNode<*>, nodeKind: NodeKind): Boolean {
      val nodePath = node.path ?: return false
      return (nodePath.find { it is MyKindNode } as? MyKindNode)?.kind == nodeKind
    }

    override fun installGroupingSupport(): ChangesGroupingSupport {
      val result = ChangesGroupingSupport(project, this, false)

      if (PropertiesComponent.getInstance(project).getList(GROUPING_PROPERTY_NAME) == null) {
        val oldGroupingKeys = (PropertiesComponent.getInstance(project).getList(GROUPING_KEYS) ?: DEFAULT_GROUPING_KEYS).toMutableSet()
        oldGroupingKeys.add(REPOSITORY_GROUPING)
        PropertiesComponent.getInstance(project).setList(GROUPING_PROPERTY_NAME, oldGroupingKeys.toList())
      }

      installGroupingSupport(this, result, GROUPING_PROPERTY_NAME, DEFAULT_GROUPING_KEYS + REPOSITORY_GROUPING)
      return result
    }

    private inner class GitStageMergeHoverIcon(private val handler: GitMergeHandler, private val conflict: GitConflict) :
      HoverIcon(AllIcons.Vcs.Merge, message("changes.view.merge.action.text")) {

      override fun invokeAction(node: ChangesBrowserNode<*>) {
        showMergeWindow(project, handler, listOf(conflict))
      }

      override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as GitStageMergeHoverIcon

        if (conflict != other.conflict) return false

        return true
      }

      override fun hashCode(): Int {
        return conflict.hashCode()
      }
    }
  }

  interface IncludedRootsListener : EventListener {
    fun includedRootsChanged()
  }

  private inner class MyGitStageTrackerListener : GitStageTrackerListener {
    override fun update() {
      this@GitStagePanel.update()
    }
  }

  private inner class MyGitChangeProviderListener : GitRefreshListener {
    override fun progressStarted() {
      runInEdt(disposableFlag) {
        updateProgressState()
      }
    }

    override fun progressStopped() {
      runInEdt(disposableFlag) {
        updateProgressState()
      }
    }

    private fun updateProgressState() {
      if (isRefreshInProgress()) {
        tree.setEmptyText(message("stage.loading.status"))
        progressStripe.startLoading()
      }
      else {
        progressStripe.stopLoading()
        tree.setEmptyText("")
      }
    }
  }

  private inner class MyChangeListListener : ChangeListListener {
    override fun changeListUpdateDone() {
      runInEdt(disposableFlag) {
        updateChangesStatusPanel()
      }
    }
  }

  private inner class MyCommitWorkflowListener : CommitWorkflowListener {
    override fun executionEnded() {
      if (hasPendingUpdates) {
        hasPendingUpdates = false
        update()
      }
    }

    override fun vcsesChanged() = Unit
    override fun executionStarted() = Unit
    override fun beforeCommitChecksStarted() = Unit
    override fun beforeCommitChecksEnded(isDefaultCommit: Boolean, result: CheckinHandler.ReturnResult) = Unit
  }

  companion object {
    @NonNls
    private const val GROUPING_PROPERTY_NAME = "GitStage.ChangesTree.GroupingKeys"
    private const val GIT_STAGE_PANEL_PLACE = "GitStagePanelPlace"
  }
}

internal fun createMergeHandler(project: Project) = GitMergeHandler(project, GitDefaultMergeDialogCustomizer(project))
