// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel.ModelProvider.ExtendedTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.tree.TreePath

internal class BackendChangesView private constructor(
  val changesPanel: JComponent,
  val viewModel: BackendCommitChangesViewModel,
): Disposable {
  init {
    Disposer.register(this, viewModel)
  }

  override fun dispose() {
  }

  companion object {
    @JvmStatic
    fun create(project: Project, parentDisposable: Disposable): BackendChangesView {
      val tree: ChangesListView = LocalChangesListView(project)
      val panel = CommitChangesViewWithToolbarPanel(tree, parentDisposable)
      return BackendChangesView(panel, BackendLocalCommitChangesViewModel(panel))
    }
  }
}

// TODO make RD-friendly implementation, cleanup methods returning tree/component
internal interface BackendCommitChangesViewModel: Disposable {
  fun initPanel()

  fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?)

  fun isToolbarHorizontal(): Boolean
  fun getToolbarComponent(): JComponent
  fun setToolbarHorizontal(horizontal: Boolean)
  fun getActions(): List<AnAction>
  fun isModelUpdateInProgress(): Boolean

  fun setBusy(busy: Boolean)
  fun scheduleRefreshNow(callback: Runnable?)
  fun scheduleDelayedRefresh()
  fun setGrouping(groupingKey: String)
  fun resetViewImmediatelyAndRefreshLater()

  fun selectFile(vFile: VirtualFile?)
  fun selectChanges(changes: List<Change>)

  @ApiStatus.Obsolete
  fun getTree(): ChangesListView
  @ApiStatus.Obsolete
  fun getPreferredFocusableComponent(): JComponent
}

private class BackendLocalCommitChangesViewModel(private val panel: CommitChangesViewWithToolbarPanel): BackendCommitChangesViewModel {
  private var commitWorkflowHandler: ChangesViewCommitWorkflowHandler? = null

  init {
    Disposer.register(this, panel)
  }

  override fun initPanel() {
    panel.initPanel(ModelProvider())
  }

  override fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?) {
    commitWorkflowHandler = handler
  }

  override fun isToolbarHorizontal(): Boolean = panel.isToolbarHorizontal

  override fun getToolbarComponent(): JComponent = panel.toolbar.component

  override fun setToolbarHorizontal(horizontal: Boolean) {
    panel.isToolbarHorizontal = horizontal
  }

  override fun getActions(): List<AnAction> = panel.toolbarActionGroup.getChildren(ActionManager.getInstance()).toList()

  override fun isModelUpdateInProgress(): Boolean = panel.changesView.isModelUpdateInProgress

  override fun setBusy(busy: Boolean) {
    panel.setBusy(busy)
  }

  override fun scheduleRefreshNow(callback: Runnable?) {
    panel.scheduleRefreshNow(callback)
  }

  override fun scheduleDelayedRefresh() {
    panel.scheduleRefresh()
  }

  override fun setGrouping(groupingKey: String) {
    panel.setGrouping(groupingKey)
  }

  override fun resetViewImmediatelyAndRefreshLater() {
    panel.resetViewImmediatelyAndRefreshLater()
  }

  override fun selectFile(vFile: VirtualFile?) {
    panel.changesView.selectFile(vFile)
  }

  override fun selectChanges(changes: List<Change>) {
    val paths: MutableList<TreePath> = ArrayList()
    for (change in changes) {
      panel.changesView.findNodePathInTree(change)?.let { paths.add(it) }
    }
    TreeUtil.selectPaths(panel.changesView, paths)
  }

  override fun getTree(): ChangesListView = panel.changesView

  override fun getPreferredFocusableComponent(): JComponent = panel.changesView.preferredFocusedComponent

  override fun dispose() {
  }

  private inner class ModelProvider : CommitChangesViewWithToolbarPanel.ModelProvider {
    override fun getModel(grouping: ChangesGroupingPolicyFactory): ExtendedTreeModel {
      val project = panel.project
      val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
      val changeLists = changeListManager.changeLists
      val unversionedFiles = changeListManager.unversionedFilesPaths

      val treeModel = ChangesViewUtil.createTreeModel(
        project,
        grouping,
        changeLists,
        unversionedFiles,
        ChangesViewSettings.getInstance(project).showIgnored
      ) { commitWorkflowHandler?.isActive == true }
      return ExtendedTreeModel(changeLists, unversionedFiles, treeModel)
    }

    override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
      commitWorkflowHandler?.synchronizeInclusion(changeLists, unversionedFiles)
    }
  }
}
