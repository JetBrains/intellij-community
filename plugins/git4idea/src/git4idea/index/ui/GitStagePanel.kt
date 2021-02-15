// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListListener
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangesViewManager.createTextStatusFactory
import com.intellij.openapi.vcs.changes.EditorTabPreview
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SideBorder
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.switcher.QuickActionProvider
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.OpenSourceUtil
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.CommitStatusPanel
import com.intellij.vcs.commit.EditedCommitNode
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.ProgressStripe
import git4idea.GitVcs
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
import git4idea.merge.GitMergeUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.status.GitChangeProvider
import java.awt.BorderLayout
import javax.swing.JPanel

internal class GitStagePanel(private val tracker: GitStageTracker,
                             isVertical: Boolean,
                             isEditorDiffPreview: Boolean,
                             disposableParent: Disposable,
                             private val activate: () -> Unit) :
  JPanel(BorderLayout()), DataProvider, Disposable {
  private val project = tracker.project

  val tree: GitStageTree
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

  init {
    tree = MyChangesTree(project)

    commitPanel = GitStageCommitPanel(tree, project)
    commitPanel.commitActionsPanel.isCommitButtonDefault = {
      !commitPanel.commitProgressUi.isDumbMode &&
      IdeFocusManager.getInstance(project).getFocusedDescendantFor(this) != null
    }
    commitPanel.commitActionsPanel.setupShortcuts(this, this)
    commitPanel.addEditedCommitListener(tree::editedCommitChanged, this)
    commitWorkflowHandler = GitStageCommitWorkflowHandler(GitStageCommitWorkflow(project), commitPanel)
    Disposer.register(this, commitPanel)

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stage.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true)
    toolbar.setTargetComponent(tree)

    PopupHandler.installPopupHandler(tree, "Git.Stage.Tree.Menu", "Git.Stage.Tree.Menu")

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
    busConnection.subscribe(GitChangeProvider.TOPIC, MyGitChangeProviderListener())
    busConnection.subscribe(ChangeListListener.TOPIC, MyChangeListListener())

    if (GitVcs.getInstance(project).changeProvider?.isRefreshInProgress == true) {
      tree.setEmptyText(message("stage.loading.status"))
      progressStripe.startLoadingImmediately()
    }
    updateChangesStatusPanel()

    Disposer.register(disposableParent, this)

    runInEdtAsync(this, { tree.rebuildTree() })
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
    commitPanel.state = state
    commitWorkflowHandler.state = state
  }

  override fun getData(dataId: String): Any? {
    if (QuickActionProvider.KEY.`is`(dataId)) return toolbar
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
    if (Disposer.isDisposed(this)) return
    if (!force && (isInEditor == (editorTabPreview != null))) return

    if (diffPreviewProcessor != null) Disposer.dispose(diffPreviewProcessor!!)
    diffPreviewProcessor = GitStageDiffPreview(project, tree, tracker, this)
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

  internal fun setCommitMessage(commitMessage: String) {
    commitPanel.commitMessage.setCommitMessage(commitMessage)
  }

  override fun dispose() {
  }

  private inner class MyChangesTree(project: Project) : GitStageTree(project, project.service<GitStageUiSettingsImpl>(), this@GitStagePanel) {
    override val state
      get() = this@GitStagePanel.state
    override val ignoredFilePaths
      get() = this@GitStagePanel.tracker.ignoredPaths
    override val operations: List<StagingAreaOperation> = listOf(GitAddOperation, GitResetOperation)

    init {
      doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false

        val dataContext = DataManager.getInstance().getDataContext(this)

        val mergeAction = ActionManager.getInstance().getAction("Git.Stage.Merge")
        val event = AnActionEvent.createFromAnAction(mergeAction, e, ActionPlaces.UNKNOWN, dataContext)
        if (ActionUtil.lastUpdateAndCheckDumb(mergeAction, event, true)) {
          ActionUtil.performActionDumbAwareWithCallbacks(mergeAction, event, dataContext)
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
  }

  private inner class MyGitStageTrackerListener : GitStageTrackerListener {
    override fun update() {
      this@GitStagePanel.update()
    }
  }

  private inner class MyGitChangeProviderListener : GitChangeProvider.ChangeProviderListener {
    override fun progressStarted() {
      runInEdt(this@GitStagePanel) {
        tree.setEmptyText(message("stage.loading.status"))
        progressStripe.startLoading()
      }
    }

    override fun progressStopped() {
      runInEdt(this@GitStagePanel) {
        progressStripe.stopLoading()
        tree.setEmptyText("")
      }
    }

    override fun repositoryUpdated(repository: GitRepository) = Unit
  }

  private inner class MyChangeListListener : ChangeListListener {
    override fun changeListUpdateDone() {
      runInEdt(this@GitStagePanel) {
        updateChangesStatusPanel()
      }
    }
  }
}

internal fun Project.isReversedRoot(root: VirtualFile): Boolean {
  return GitRepositoryManager.getInstance(this).getRepositoryForRootQuick(root)?.let { repository ->
    GitMergeUtil.isReverseRoot(repository)
  } ?: false
}

internal fun createMergeHandler(project: Project) = GitMergeHandler(project, GitDefaultMergeDialogCustomizer(project))