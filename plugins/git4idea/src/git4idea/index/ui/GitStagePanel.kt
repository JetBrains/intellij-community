// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.ide.DataManager
import com.intellij.ide.dnd.DnDActionInfo
import com.intellij.ide.dnd.DnDDragStartBean
import com.intellij.ide.dnd.DnDEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeDnDSupport
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.OpenSourceUtil
import com.intellij.util.Processor
import com.intellij.util.ui.UIUtil
import com.intellij.vcs.commit.showEmptyCommitMessageConfirmation
import com.intellij.vcs.log.runInEdt
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.ProgressStripe
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.index.CommitListener
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
import git4idea.index.actions.*
import git4idea.repo.GitRepository
import git4idea.status.GitChangeProvider
import org.jetbrains.annotations.CalledInAwt
import java.awt.BorderLayout
import javax.swing.JPanel

val GIT_STAGE_TRACKER = DataKey.create<GitStageTracker>("GitStageTracker")

internal class GitStagePanel(private val tracker: GitStageTracker, disposableParent: Disposable) :
  JPanel(BorderLayout()), DataProvider, Disposable {
  private val project = tracker.project

  private val tree: GitStageTree
  private val commitPanel: GitCommitPanel
  private val progressStripe: ProgressStripe

  private val state: GitStageTracker.State
    get() = tracker.state

  private var isCommitInProgress = false
  private var hasPendingUpdates = false

  init {
    tree = MyChangesTree(project)
    commitPanel = MyGitCommitPanel()

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stage.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true)
    toolbar.setTargetComponent(tree)

    PopupHandler.installPopupHandler(tree, "Git.Stage.Tree.Menu", "Git.Stage.Tree.Menu")

    val scrolledTree = ScrollPaneFactory.createScrollPane(tree, SideBorder.TOP)
    progressStripe = ProgressStripe(scrolledTree, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    val treeMessageSplitter = OnePixelSplitter(true, "git.stage.tree.message.splitter", 0.7f)
    treeMessageSplitter.firstComponent = progressStripe
    treeMessageSplitter.secondComponent = commitPanel

    val leftPanel = JPanel(BorderLayout())
    leftPanel.add(toolbar.component, BorderLayout.NORTH)
    leftPanel.add(treeMessageSplitter, BorderLayout.CENTER)

    val diffPreview = GitStageDiffPreview(project, tree, tracker, this)
    diffPreview.getToolbarWrapper().setVerticalSizeReferent(toolbar.component)

    val commitDiffSplitter = OnePixelSplitter("git.stage.commit.diff.splitter", 0.5f)
    commitDiffSplitter.firstComponent = leftPanel
    commitDiffSplitter.secondComponent = diffPreview.component

    add(commitDiffSplitter, BorderLayout.CENTER)

    tracker.addListener(MyGitStageTrackerListener(), this)
    project.messageBus.connect(this).subscribe(GitChangeProvider.TOPIC, MyGitChangeProviderListener())
    if (GitVcs.getInstance(project).changeProvider?.isRefreshInProgress == true) {
      tree.setEmptyText(GitBundle.message("stage.loading.status"))
      progressStripe.startLoadingImmediately()
    }

    Disposer.register(disposableParent, this)

    runInEdtAsync(this, { tree.rebuildTree() })
  }

  private fun performCommit(amend: Boolean) {
    val rootsToCommit = state.stagedRoots
    if (rootsToCommit.isEmpty()) return

    val commitMessage = commitPanel.commitMessage.text
    if (commitMessage.isBlank() && !showEmptyCommitMessageConfirmation()) return

    commitStarted()

    FileDocumentManager.getInstance().saveAllDocuments()
    git4idea.index.performCommit(project, rootsToCommit, commitMessage, amend, MyCommitListener(commitMessage))
  }

  @CalledInAwt
  private fun commitStarted() {
    isCommitInProgress = true
    commitPanel.commitButton.isEnabled = false
  }

  @CalledInAwt
  private fun commitFinished(success: Boolean) {
    isCommitInProgress = false
    // commit button is going to be enabled after state update
    if (success) commitPanel.isAmend = false
    if (hasPendingUpdates) {
      hasPendingUpdates = false
      update()
    }
  }

  @CalledInAwt
  fun update() {
    if (isCommitInProgress) {
      hasPendingUpdates = true
      return
    }
    tree.update()
    commitPanel.commitButton.isEnabled = state.hasStagedRoots()
  }

  override fun getData(dataId: String): Any? {
    if (GIT_STAGE_TRACKER.`is`(dataId)) return tracker
    return null
  }

  override fun dispose() {
  }

  private inner class MyChangesTree(project: Project) : GitStageTree(project, this) {
    override val state
      get() = this@GitStagePanel.state
    override val operations: List<StagingAreaOperation> = listOf(GitAddOperation, GitResetOperation)

    init {
      doubleClickHandler = Processor { e ->
        if (EditSourceOnDoubleClickHandler.isToggleEvent(this, e)) return@Processor false

        OpenSourceUtil.openSourcesFrom(DataManager.getInstance().getDataContext(this), true)
        true
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
  }

  private inner class MyGitCommitPanel : GitCommitPanel(project, this) {
    override fun isFocused(): Boolean {
      return IdeFocusManager.getInstance(project).getFocusedDescendantFor(this@GitStagePanel) != null
    }

    override fun performCommit() {
      performCommit(isAmend)
    }

    override fun rootsToCommit() = state.stagedRoots.map { VcsRoot(GitVcs.getInstance(project), it) }
  }

  private inner class MyGitStageTrackerListener : GitStageTrackerListener {
    override fun update() {
      this@GitStagePanel.update()
    }
  }

  private inner class MyGitChangeProviderListener : GitChangeProvider.ChangeProviderListener {
    override fun progressStarted() {
      runInEdt(this@GitStagePanel) {
        tree.setEmptyText(GitBundle.message("stage.loading.status"))
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

  private inner class MyCommitListener(private val commitMessage: String) : CommitListener {
    private val notifier = VcsNotifier.getInstance(project)

    override fun commitProcessFinished(successfulRoots: Collection<VirtualFile>, failedRoots: Map<VirtualFile, VcsException>) {
      commitFinished(successfulRoots.isNotEmpty() && failedRoots.isEmpty())

      if (successfulRoots.isNotEmpty()) {
        notifier.notifySuccess(GitBundle.message("stage.commit.successful", successfulRoots.joinToString {
          "'${VcsImplUtil.getShortVcsRootName(project, it)}'"
        }, XmlStringUtil.escapeString(commitMessage)))
      }
      if (failedRoots.isNotEmpty()) {
        notifier.notifyError(GitBundle.message("stage.commit.failed", failedRoots.keys.joinToString {
          "'${VcsImplUtil.getShortVcsRootName(project, it)}'"
        }), failedRoots.values.joinToString(UIUtil.BR) { it.localizedMessage })
      }
    }
  }
}