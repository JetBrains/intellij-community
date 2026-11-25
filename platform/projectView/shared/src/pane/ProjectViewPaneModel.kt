// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.openapi.project.Project
import com.intellij.platform.projectView.settings.ProjectViewPaneFileNestingValue
import com.intellij.platform.projectView.settings.ProjectViewPaneOption
import com.intellij.platform.projectView.settings.ProjectViewPaneSortKey
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewPaneModel {
  val project: Project
  val cutCopyPasteDeleteHandler: ProjectViewPaneCutCopyPasteDeleteHandler
  val dndHandler: ProjectViewPaneDnDHandler
  
  suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor

  suspend fun manageState(builder: ProjectViewPaneStateBuilder)

  suspend fun setPaneSelected(isSelected: Boolean, options: ProjectViewPaneSelectionOptions)

  suspend fun loadChildren(parentId: Long, options: ProjectViewPaneLoadChildrenOptions)

  suspend fun navigate(nodeIds: List<Long>, options: ProjectViewPaneNavigateOptions): Boolean

  suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean)

  suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey)

  suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue)

  fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot)
  
  fun getDataContext(nodeIds: List<Long>): DataContext

  /**
   * Tells if this pane can select the [request], before [findNodeForSelectIn] searches the tree.
   *
   * Select In calls it in a read action, sometimes on the EDT, so it must be fast.
   * The pane can be inactive, so the check must not use the tree of the pane.
   * The default implementation returns `true`.
   *
   * A [SelectByEditor] request has no context to check.
   * The implementations of the platform return `true` for it, and [findNodeForSelectIn] decides.
   */
  @RequiresReadLock
  fun canSelect(request: SelectInRequest): Boolean = true

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath?
  
  suspend fun refreshSettings() { }
}

/** Reserved for future use. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSelectionOptions

internal data object ProjectViewPaneSelectionOptionsImpl : ProjectViewPaneSelectionOptions

/** Reserved for future use. */
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneLoadChildrenOptions

internal data object ProjectViewPaneLoadChildrenOptionsImpl : ProjectViewPaneLoadChildrenOptions

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneNavigateOptions {
  val requestFocus: Boolean
  val openInRightSplit: Boolean
}

internal data class ProjectViewPaneNavigateOptionsImpl(
  override val requestFocus: Boolean,
  override val openInRightSplit: Boolean = false,
) : ProjectViewPaneNavigateOptions
