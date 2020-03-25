// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.progress.util.ProgressWindow
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.vcs.log.ui.frame.ProgressStripe
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
  private val progressStripe: ProgressStripe

  private val state: GitStageTracker.State
    get() = tracker.state

  init {
    tree = MyChangesTree(project)

    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction("Git.Stage.Toolbar"))
    toolbarGroup.addSeparator()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true)
    toolbar.setTargetComponent(tree)

    val scrolledTree = ScrollPaneFactory.createScrollPane(tree, SideBorder.TOP)
    progressStripe = ProgressStripe(scrolledTree, this, ProgressWindow.DEFAULT_PROGRESS_DIALOG_POSTPONE_TIME_MILLIS)
    add(toolbar.component, BorderLayout.NORTH)
    add(progressStripe, BorderLayout.CENTER)

    tracker.addListener(MyGitStageTrackerListener(), this)
    if (tracker.isRefreshInProgress) {
      tree.setEmptyText(GitBundle.message("stage.loading.status"))
      progressStripe.startLoadingImmediately()
    }
    tracker.scheduleUpdateAll()

    Disposer.register(disposableParent, this)
  }

  fun update() {
    tree.update()
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