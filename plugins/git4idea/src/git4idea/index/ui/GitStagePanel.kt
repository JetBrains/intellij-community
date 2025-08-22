// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.ui

import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.util.DiffUtil
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.DiffPreview.Companion.setPreviewVisible
import com.intellij.openapi.vcs.changes.actions.ShowDiffPreviewAction
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.shouldHaveSplitterDiffPreview
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.vcs.impl.shared.changes.PreviewDiffSplitterComponent
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EventDispatcher
import com.intellij.util.OpenSourceUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.*
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.CommitWorkflowListener
import com.intellij.vcs.commit.insertEditedCommitNode
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.ui.ProgressStripe
import git4idea.GitVcs
import git4idea.conflicts.GitConflictsUtil.canShowMergeWindow
import git4idea.conflicts.GitConflictsUtil.showMergeWindow
import git4idea.conflicts.GitMergeHandler
import git4idea.i18n.GitBundle.message
import git4idea.index.GitStageCommitWorkflow
import git4idea.index.GitStageCommitWorkflowHandler
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.actions.*
import git4idea.merge.GitDefaultMergeDialogCustomizer
import git4idea.repo.GitConflict
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.status.GitRefreshListener
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.awt.event.MouseEvent
import java.beans.PropertyChangeListener
import java.util.*
import javax.swing.JPanel

internal class GitStagePanel(
  private val tracker: GitStageTracker,
  private val isVertical: () -> Boolean,
  disposableParent: Disposable,
  private val activate: () -> Unit,
) : JPanel(BorderLayout()), UiDataProvider, Disposable {
  private val project = tracker.project
  private val disposableFlag = Disposer.newCheckedDisposable()

  private val _tree: MyChangesTree
  val tree: ChangesTree get() = _tree
  private val progressStripe: ProgressStripe
  private val toolbar: ActionToolbar
  private val commitPanel: GitStageCommitPanel
  private val changesStatusPanel: Wrapper

  private val mainPanelContent = Wrapper()
  private val treeMessageSplitter: Splitter

  private val commitWorkflowHandler: GitStageCommitWorkflowHandler

  private var splitPreview: ShelveSplitterDiffPreview? = null
  private val editorTabPreview: GitStageEditorDiffPreview

  private val state: GitStageTracker.State
    get() = tracker.state
  private var isDisposed = false

  private var hasPendingUpdates = false

  internal val commitMessage get() = commitPanel.commitMessage

  init {
    _tree = MyChangesTree(project)

    commitPanel = GitStageCommitPanel(project, GitStageUiSettingsImpl(project))
    commitPanel.commitActionsPanel.isCommitButtonDefault = {
      !commitPanel.commitProgressUi.isDumbMode &&
      IdeFocusManager.getInstance(project).getFocusedDescendantFor(this) != null
    }
    commitPanel.commitActionsPanel.createActions().forEach { it.registerCustomShortcutSet(this, this) }
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
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions())
    toolbarGroup.add(Separator.getInstance())
    toolbarGroup.add(MyToggleDetailsAction())
    toolbar = ActionManager.getInstance().createActionToolbar(GIT_STAGE_PANEL_PLACE, toolbarGroup, true)
    toolbar.targetComponent = tree

    PopupHandler.installPopupMenu(tree, "Git.Stage.Tree.Menu", "Git.Stage.Tree.Menu")

    val sideBorder = if (ExperimentalUI.isNewUI()) SideBorder.NONE else SideBorder.TOP
    val treePanel = GitStageTreePanel()
      .addToCenter(createScrollPane(tree, sideBorder))
    progressStripe = ProgressStripe(treePanel, this)

    val treePanelWithToolbar = JPanel(BorderLayout())
    treePanelWithToolbar.add(toolbar.component, BorderLayout.NORTH)
    treePanelWithToolbar.add(progressStripe, BorderLayout.CENTER)

    treeMessageSplitter = TwoKeySplitter(true, ProportionKey("git.stage.tree.message.splitter", 0.7f,
                                                             "git.stage.tree.message.splitter.horizontal", 0.5f))
    treeMessageSplitter.firstComponent = treePanelWithToolbar
    treeMessageSplitter.secondComponent = commitPanel.component

    changesStatusPanel = Wrapper()
    changesStatusPanel.minimumSize = JBUI.emptySize()

    mainPanelContent.setContent(treeMessageSplitter)

    add(mainPanelContent, BorderLayout.CENTER)
    add(changesStatusPanel, BorderLayout.SOUTH)

    editorTabPreview = GitStageEditorDiffPreview(_tree, tracker, toolbar.component, activate)

    // Override the handlers registered by editorTabPreview
    tree.doubleClickHandler = Processor { e ->
      if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
      processDoubleClickEvent(e)
      true
    }
    tree.enterKeyHandler = Processor {
      processEnterEvent()
      true
    }

    updateLayout()

    tracker.addListener(MyGitStageTrackerListener(), this)
    val busConnection = project.messageBus.connect(this)
    busConnection.subscribe(GitRefreshListener.TOPIC, MyGitChangeProviderListener())
    busConnection.subscribe(ChangeListListener.TOPIC, MyChangeListListener())
    busConnection.subscribe(VcsManagedFilesHolder.TOPIC, VcsManagedFilesHolder.VcsManagedFilesHolderListener {
      runInEdt(disposableFlag) { updateProgressState() }
    })
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
    if (GitVcs.getInstance(project).changeProvider!!.isRefreshInProgress) {
      LOG.trace("Change provider is refreshing")
      return true
    }
    return GitRepositoryManager.getInstance(project).repositories.any {
      val updateModeForFilesHolder = it.untrackedFilesHolder.isInUpdateMode || it.ignoredFilesHolder.isInUpdateMode()
      LOG.trace("Repository $it files holder update state - $updateModeForFilesHolder")
      updateModeForFilesHolder
    }
  }

  private fun updateChangesStatusPanel() {
    val manager = ChangeListManagerImpl.getInstanceImpl(project)
    val components = manager.additionalUpdateInfo.mapNotNull { it.get() }
    changesStatusPanel.setContent(DiffUtil.createStackedComponents(components, DiffUtil.TITLE_GAP))
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

  override fun uiDataSnapshot(sink: DataSink) {
    sink[QuickActionProvider.KEY] = toolbar as? QuickActionProvider
    sink[DiffDataKeys.EDITOR_TAB_DIFF_PREVIEW] = editorTabPreview
    sink[PlatformDataKeys.HELP_ID] = HELP_ID

    // This makes COMMIT_WORKFLOW_HANDLER available anywhere in "Local Changes" - so commit executor actions are enabled.
    commitPanel.uiDataSnapshotFromProviders(sink)
  }

  @Suppress("KotlinConstantConditions")
  internal fun updateLayout() {
    if (disposableFlag.isDisposed) return

    val isVertical = isVertical()
    val hasSplitterPreview = shouldHaveSplitterDiffPreview(project, isVertical)
    val isPreviewPanelShown = hasSplitterPreview && VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN
    val isMessageSplitterVertical = isVertical || isPreviewPanelShown;
    if (treeMessageSplitter.orientation != isMessageSplitterVertical) {
      treeMessageSplitter.orientation = isMessageSplitterVertical
    }

    val needUpdatePreviews = hasSplitterPreview != (splitPreview != null)
    if (!needUpdatePreviews) return

    if (hasSplitterPreview) {
      val preview = ShelveSplitterDiffPreview()
      setPreviewVisible(preview, VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN)
      splitPreview = preview
    }
    else {
      splitPreview?.let { Disposer.dispose(it) }
      splitPreview = null
    }
  }

  private fun processDoubleClickEvent(e: MouseEvent) {
    if (performMergeAction(project, _tree.selectedStatusNodes())) return

    if (editorTabPreview.handleDoubleClick(e) == true) return

    val dataContext = DataManager.getInstance().getDataContext(tree)
    OpenSourceUtil.openSourcesFrom(dataContext, true)
  }

  private fun processEnterEvent() {
    if (performMergeAction(project, _tree.selectedStatusNodes())) return

    if (editorTabPreview.handleEnterKey() == true) return

    val dataContext = DataManager.getInstance().getDataContext(tree)
    OpenSourceUtil.openSourcesFrom(dataContext, true)
  }

  override fun dispose() {
    isDisposed = true
    Disposer.dispose(editorTabPreview)

    splitPreview?.let { Disposer.dispose(it) }
    splitPreview = null
  }

  private fun ChangesTree.setDefaultEmptyText() {
    emptyText.setText(message("stage.default.status"))
    if (!wasStagingAreaActionInvoked()) {
      emptyText.appendLine("")
        .appendLine(AllIcons.General.ContextHelp, message("stage.default.status.help"), SimpleTextAttributes.LINK_PLAIN_ATTRIBUTES) {
          HelpManager.getInstance().invokeHelp(HELP_ID)
        }
    }
  }

  private inner class ShelveSplitterDiffPreview : DiffPreview, Disposable {
    private val processor: GitStageDiffRequestProcessor
    private val splitterComponent: PreviewDiffSplitterComponent

    init {
      processor = GitStageDiffRequestProcessor(_tree, tracker, false)
      processor.setToolbarVerticalSizeReferent(toolbar.component)

      splitterComponent = PreviewDiffSplitterComponent(processor, "git.stage.commit.diff.splitter")

      splitterComponent.firstComponent = treeMessageSplitter
      mainPanelContent.setContent(splitterComponent)
    }

    override fun dispose() {
      Disposer.dispose(processor)

      if (!this@GitStagePanel.isDisposed) {
        mainPanelContent.setContent(treeMessageSplitter)
      }
    }

    override fun openPreview(requestFocus: Boolean): Boolean {
      return splitterComponent.openPreview(requestFocus)
    }

    override fun closePreview() {
      splitterComponent.closePreview()
    }
  }

  private inner class MyToggleDetailsAction : ShowDiffPreviewAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.isEnabledAndVisible = splitPreview != null
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      VcsConfiguration.getInstance(project).LOCAL_CHANGES_DETAILS_PREVIEW_SHOWN = state

      val preview = splitPreview ?: return
      setPreviewVisible(preview, state)
      updateLayout()
    }
  }

  private inner class MyChangesTree(project: Project) : GitStageTree(project, GitStageUiSettingsImpl(project),
                                                                     this@GitStagePanel) {
    override val state
      get() = this@GitStagePanel.state
    override val ignoredFilePaths
      get() = this@GitStagePanel.tracker.ignoredPaths
    override val operations: List<StagingAreaOperation> = listOf(GitAddOperation, GitResetOperation)

    private val includedRootsListeners = EventDispatcher.create(IncludedRootsListener::class.java)

    init {
      isShowCheckboxes = true
      setDefaultEmptyText()

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
    }

    fun editedCommitChanged() {
      requestRefresh {
        commitPanel.editedCommit?.let {
          val node = TreeUtil.findNodeWithObject(root, it)
          node?.let { expandPath(TreeUtil.getPathFromRoot(node)) }
        }
      }
    }

    override fun customizeTreeModel(builder: TreeModelBuilder) {
      super.customizeTreeModel(builder)

      commitPanel.editedCommit?.let { editedCommit ->
        insertEditedCommitNode(builder, editedCommit)
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
        .iterateRawNodes()
        .filter { node -> isIncludable(node) }
        .map { node -> node.userObject }
        .toList()
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

        return conflict == other.conflict
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
  }

  private class GitStageTreePanel : BorderLayoutPanel() {
    override fun updateUI() {
      super.updateUI()
      background = UIUtil.getTreeBackground()
    }
  }

  private fun updateProgressState() {
    if (isRefreshInProgress()) {
      tree.setEmptyText(message("stage.loading.status"))
      progressStripe.startLoading()
    }
    else {
      progressStripe.stopLoading()
      tree.setDefaultEmptyText()
    }
  }

  companion object {
    @NonNls
    private const val GROUPING_PROPERTY_NAME = "GitStage.ChangesTree.GroupingKeys"
    private const val GIT_STAGE_PANEL_PLACE = "GitStagePanelPlace"
    internal const val HELP_ID = "reference.VersionControl.Git.StagingArea"

    private val LOG = Logger.getInstance(GitStagePanel::class.java)
  }
}

internal fun createMergeHandler(project: Project) = GitMergeHandler(project, GitDefaultMergeDialogCustomizer(project))

private const val GIT_STAGE_ACTION_INVOKED_PROPERTY = "git.stage.action.invoked"
internal fun stagingAreaActionInvoked() = PropertiesComponent.getInstance().setValue(GIT_STAGE_ACTION_INVOKED_PROPERTY, true)
internal fun wasStagingAreaActionInvoked() = PropertiesComponent.getInstance().getBoolean(GIT_STAGE_ACTION_INVOKED_PROPERTY, false)