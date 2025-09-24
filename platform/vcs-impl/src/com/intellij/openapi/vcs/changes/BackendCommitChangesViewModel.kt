// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel.ModelProvider.ExtendedTreeModel
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.kernel.ids.BackendValueIdType
import com.intellij.platform.kernel.ids.storeValueGlobally
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.RdLocalChanges
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.ui.components.JBLabel
import com.intellij.ui.split.createComponent
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.tree.TreePath

internal class BackendChangesView private constructor(
  val changesPanel: JComponent,
  val viewModel: BackendCommitChangesViewModel,
) {
  companion object {
    @JvmStatic
    fun create(project: Project, parentDisposable: Disposable): BackendChangesView {
      val scope = project.service<ScopeProvider>().cs.childScope("CommitChangesViewWithToolbarPanel")
      Disposer.register(parentDisposable) { scope.cancel() }

      if (RdLocalChanges.isEnabled()) {
        val viewModel = BackendRemoteCommitChangesViewModel(project)
        val  id = storeValueGlobally(scope, viewModel, BackendChangesViewValueIdType)
        val panel = ChangesViewSplitComponentBinding.createComponent(project, scope, id)
        return BackendChangesView(panel, viewModel)
      } else {
        val panel = CommitChangesViewWithToolbarPanel(LocalChangesListView(project), scope)
        val backendChangesView = BackendChangesView(panel, BackendLocalCommitChangesViewModel(panel))
        val id = storeValueGlobally(scope, backendChangesView.viewModel, BackendChangesViewValueIdType)
        panel.id = id
        return backendChangesView
      }
    }
  }

  @Service(Service.Level.PROJECT)
  internal class ScopeProvider(val cs: CoroutineScope)
}

// TODO make RD-friendly implementation, cleanup methods returning tree/component
internal interface BackendCommitChangesViewModel {
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

// FIXME
private class BackendRemoteCommitChangesViewModel(private val project: Project) : BackendCommitChangesViewModel {
  private var horizontal: Boolean = true
  private val treeView: ChangesListView by lazy { LocalChangesListView(project) }

  override fun initPanel() {
  }

  override fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?) {
  }

  override fun isToolbarHorizontal(): Boolean = horizontal

  override fun getToolbarComponent(): JComponent = JBLabel("Toolbar (RD placeholder)")

  override fun setToolbarHorizontal(horizontal: Boolean) {
    this.horizontal = horizontal
  }

  override fun getActions(): List<AnAction> = emptyList()

  override fun isModelUpdateInProgress(): Boolean = false

  override fun setBusy(busy: Boolean) {
  }

  override fun scheduleRefreshNow(callback: Runnable?) {
  }

  override fun scheduleDelayedRefresh() {
  }

  override fun setGrouping(groupingKey: String) {
  }

  override fun resetViewImmediatelyAndRefreshLater() {
  }

  override fun selectFile(vFile: VirtualFile?) {
  }

  override fun selectChanges(changes: List<Change>) {
  }

  override fun getTree(): ChangesListView = treeView

  override fun getPreferredFocusableComponent(): JComponent = treeView.preferredFocusedComponent
}

private class BackendLocalCommitChangesViewModel(private val panel: CommitChangesViewWithToolbarPanel): BackendCommitChangesViewModel {
  private var commitWorkflowHandler: ChangesViewCommitWorkflowHandler? = null

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

private object BackendChangesViewValueIdType : BackendValueIdType<ChangesViewId, BackendCommitChangesViewModel>(::ChangesViewId)