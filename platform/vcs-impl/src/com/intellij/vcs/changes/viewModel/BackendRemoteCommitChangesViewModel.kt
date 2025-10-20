// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.LocalChangesListView
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import javax.swing.JComponent

// TODO IJPL-173924 Propper RPC-based implementation
internal class BackendRemoteCommitChangesViewModel(private val project: Project) : BackendCommitChangesViewModel {
  private var horizontal: Boolean = true
  private val treeView: ChangesListView by lazy { LocalChangesListView(project) }
  override val inclusionChanged = MutableSharedFlow<Unit>()

  private val inclusionModel = MutableStateFlow<InclusionModel?>(null)

  override fun setInclusionModel(model: InclusionModel?) {
    inclusionModel.value = model
  }

  override fun initPanel() {
  }

  override fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?) {
  }

  override fun setToolbarHorizontal(horizontal: Boolean) {
    this.horizontal = horizontal
  }

  override fun getActions(): List<AnAction> = emptyList()

  override fun isModelUpdateInProgress(): Boolean = false

  override fun scheduleRefreshNow(callback: Runnable?) {
  }

  override fun scheduleDelayedRefresh() {
  }

  override fun setGrouping(groupingKey: String) {
  }

  override fun resetViewImmediatelyAndRefreshLater() {
  }

  override fun setShowCheckboxes(value: Boolean) {}

  override fun getDisplayedChanges(): List<Change> = emptyList()

  override fun getIncludedChanges(): List<Change> = emptyList()

  override fun getDisplayedUnversionedFiles(): List<FilePath> = emptyList()

  override fun getIncludedUnversionedFiles(): List<FilePath> = emptyList()

  override fun expand(item: Any) {
  }

  override fun select(item: Any) {
  }

  override fun selectFirst(items: Collection<Any>) {
  }

  override fun selectFile(vFile: VirtualFile?) {
  }

  override fun selectChanges(changes: List<Change>) {
  }

  override fun getTree(): ChangesListView = treeView

  override fun getPreferredFocusableComponent(): JComponent = treeView.preferredFocusedComponent
}
