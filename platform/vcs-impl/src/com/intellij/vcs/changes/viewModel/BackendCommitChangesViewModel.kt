// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes.viewModel

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.InclusionModel
import com.intellij.openapi.vcs.changes.ui.ChangesListView
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.commit.ChangesViewCommitWorkflowHandler
import kotlinx.coroutines.flow.SharedFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

// TODO IJPL-173924 cleanup methods returning tree/component
internal interface BackendCommitChangesViewModel {
  val inclusionChanged: SharedFlow<Unit>

  fun initPanel()

  fun setCommitWorkflowHandler(handler: ChangesViewCommitWorkflowHandler?)

  fun setToolbarHorizontal(horizontal: Boolean)
  fun getActions(): List<AnAction>
  fun isModelUpdateInProgress(): Boolean

  fun scheduleRefreshNow(callback: Runnable?)
  fun scheduleDelayedRefresh()
  fun setGrouping(groupingKey: String)
  fun resetViewImmediatelyAndRefreshLater()

  fun setInclusionModel(model: InclusionModel?)
  fun setShowCheckboxes(value: Boolean)

  fun getDisplayedChanges(): List<Change>
  fun getIncludedChanges(): List<Change>
  fun getDisplayedUnversionedFiles(): List<FilePath>
  fun getIncludedUnversionedFiles(): List<FilePath>

  fun expand(item: Any)
  fun select(item: Any)
  fun selectFirst(items: Collection<Any>)

  fun selectFile(vFile: VirtualFile?)
  fun selectChanges(changes: List<Change>)

  @ApiStatus.Obsolete
  fun getTree(): ChangesListView

  @ApiStatus.Obsolete
  fun getPreferredFocusableComponent(): JComponent
}
