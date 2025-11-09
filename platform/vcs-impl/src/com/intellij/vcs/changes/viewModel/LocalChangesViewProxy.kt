// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitChangesViewWithToolbarPanel
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.UNVERSIONED_FILES_TAG
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.tree.TreeUtil.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import javax.swing.tree.TreePath

/**
 * Suitable for the monolith mode only.
 */
internal class LocalChangesViewProxy(override val panel: CommitChangesViewWithToolbarPanel, scope: CoroutineScope) : ChangesViewProxy(scope) {
  override val inclusionChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun setInclusionModel(model: InclusionModel?) {
    panel.changesView.setInclusionModel(model)
  }

  override fun initPanel() {
    panel.initPanel()
    panel.changesView.setInclusionListener { inclusionChanged.tryEmit(Unit) }
  }

  override fun setToolbarHorizontal(horizontal: Boolean) {
    panel.isToolbarHorizontal = horizontal
  }

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
}
