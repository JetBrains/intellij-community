// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.vcs.commit.showEmptyCommitMessageConfirmation
import com.intellij.vcs.log.runInEdtAsync
import com.intellij.vcs.log.ui.frame.ProgressStripe
import git4idea.GitVcs
import git4idea.i18n.GitBundle
import git4idea.index.GitStageTracker
import git4idea.index.GitStageTrackerListener
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
    if (tracker.isRefreshInProgress) {
      tree.setEmptyText(GitBundle.message("stage.loading.status"))
      progressStripe.startLoadingImmediately()
    }

    Disposer.register(disposableParent, this)

    runInEdtAsync(this, { tree.rebuildTree() })
  }

  private fun performCommit(amend: Boolean) {
    val rootsToCommit = state.stagedRoots
    if (rootsToCommit.isEmpty()) return

    if (commitPanel.commitMessage.text.isBlank() && !showEmptyCommitMessageConfirmation()) return

    git4idea.index.performCommit(project, rootsToCommit, commitPanel.commitMessage.text, amend)
  }

  fun update() {
    tree.update()
    commitPanel.commitButton.isEnabled = state.hasStagedRoots()
  }

  override fun getData(dataId: String): Any? {
    if (GIT_STAGE_TRACKER.`is`(dataId)) return tracker
    return null
  }

  override fun dispose() {
  }

  inner class MyChangesTree(project: Project) : GitStageTree(project) {
    override val state
      get() = this@GitStagePanel.state
  }

  inner class MyGitCommitPanel : GitCommitPanel(project, this) {
    override fun isFocused(): Boolean {
      return IdeFocusManager.getInstance(project).getFocusedDescendantFor(this@GitStagePanel) != null
    }

    override fun performCommit() {
      performCommit(isAmend)
    }

    override fun rootsToCommit() = state.stagedRoots.map { VcsRoot(GitVcs.getInstance(project), it) }
  }

  inner class MyGitStageTrackerListener : GitStageTrackerListener {
    override fun update() {
      this@GitStagePanel.update()
    }

    override fun progressStarted() {
      tree.setEmptyText(GitBundle.message("stage.loading.status"))
      progressStripe.startLoading()
    }

    override fun progressStopped() {
      progressStripe.stopLoading()
      tree.setEmptyText("")
    }
  }
}