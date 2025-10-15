// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.changes.ChangesViewSettings
import com.intellij.util.ui.tree.TreeUtil.*
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.swing.JComponent
import javax.swing.tree.TreePath

/**
 * Simply calls delegates to the corresponding [panel] methods.
 * Suitable for the monolith mode only.
 */
internal class BackendLocalCommitChangesViewModel(val panel: CommitChangesViewWithToolbarPanel) : BackendCommitChangesViewModel {
  private var commitWorkflowHandler: ChangesViewCommitWorkflowHandler? = null
  private val _inclusionChanged = MutableSharedFlow<Unit>()

  override val inclusionChanged = _inclusionChanged.asSharedFlow()

  override fun setInclusionModel(model: InclusionModel?) {
    panel.changesView.setInclusionModel(model)
  }

  override fun initPanel() {
    panel.initPanel(ModelProvider())
    panel.changesView.setInclusionListener { _inclusionChanged.tryEmit(Unit) }
  }

  override fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?) {
    commitWorkflowHandler = handler
  }

  override fun setToolbarHorizontal(horizontal: Boolean) {
    panel.isToolbarHorizontal = horizontal
  }

  override fun getActions(): List<AnAction> = panel.toolbarActionGroup.getChildren(ActionManager.getInstance()).toList()

  override fun isModelUpdateInProgress(): Boolean = panel.changesView.isModelUpdateInProgress

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

  override fun setShowCheckboxes(value: Boolean) {
    panel.changesView.isShowCheckboxes = value
  }

  override fun getDisplayedChanges(): List<Change> = all(panel.changesView).userObjects(Change::class.java)

  override fun getIncludedChanges(): List<Change> = included(panel.changesView).userObjects(Change::class.java)

  override fun getDisplayedUnversionedFiles(): List<FilePath> = allUnderTag(panel.changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override fun getIncludedUnversionedFiles(): List<FilePath> = includedUnderTag(panel.changesView, UNVERSIONED_FILES_TAG).userObjects(FilePath::class.java)

  override fun expand(item: Any) {
    val node = panel.changesView.findNodeInTree(item)
    node?.let { panel.changesView.expandSafe(it) }
  }

  override fun select(item: Any) {
    val path = panel.changesView.findNodePathInTree(item)
    path?.let { selectPath(panel.changesView, it, false) }
  }

  override fun selectFirst(items: Collection<Any>) {
    if (items.isEmpty()) return
    val path = treePathTraverser(panel.changesView).preOrderDfsTraversal().find { getLastUserObject(it) in items }
    path?.let { selectPath(panel.changesView, it, false) }
  }

  override fun selectFile(vFile: VirtualFile?) {
    panel.changesView.selectFile(vFile)
  }

  override fun selectChanges(changes: List<Change>) {
    val paths: MutableList<TreePath> = ArrayList()
    for (change in changes) {
      panel.changesView.findNodePathInTree(change)?.let { paths.add(it) }
    }
    selectPaths(panel.changesView, paths)
  }

  override fun getTree(): ChangesListView = panel.changesView

  override fun getPreferredFocusableComponent(): JComponent = panel.changesView.preferredFocusedComponent

  private inner class ModelProvider : CommitChangesViewWithToolbarPanel.ModelProvider {
    override fun getModelData(): CommitChangesViewWithToolbarPanel.ModelProvider.ModelData {
      val project = panel.project
      val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
      val changeLists = changeListManager.changeLists
      val unversionedFiles = changeListManager.unversionedFilesPaths

      val ignoredFiles = if (ChangesViewSettings.getInstance(project).showIgnored) changeListManager.ignoredFilePaths else emptyList()
      return CommitChangesViewWithToolbarPanel.ModelProvider.ModelData(
        changeLists,
        unversionedFiles,
        ignoredFiles,
      ) { commitWorkflowHandler?.isActive == true }
    }

    override fun synchronizeInclusion(changeLists: List<LocalChangeList>, unversionedFiles: List<FilePath>) {
      commitWorkflowHandler?.synchronizeInclusion(changeLists, unversionedFiles)
    }
  }
}
