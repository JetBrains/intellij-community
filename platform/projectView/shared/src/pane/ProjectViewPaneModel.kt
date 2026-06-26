// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.DataSnapshot
import com.intellij.platform.projectView.actions.EditorChoice
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface ProjectViewPaneModel {
  suspend fun describe(builder: ProjectViewPaneDescriptorBuilder): ProjectViewPaneDescriptor

  suspend fun manageState(builder: ProjectViewPaneStateBuilder)

  suspend fun setSelected(isSelected: Boolean, options: ProjectViewPaneSelectionOptions)

  suspend fun loadChildren(parentId: Long, options: ProjectViewPaneLoadChildrenOptions)

  suspend fun navigate(nodeId: Long, options: ProjectViewPaneNavigateOptions)

  suspend fun setOptionValue(option: ProjectViewPaneOption, newValue: Boolean)

  suspend fun setSortKey(sortKeyValue: ProjectViewPaneSortKey)

  suspend fun setFileNesting(fileNestingValue: ProjectViewPaneFileNestingValue)

  fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot)

  suspend fun findNodeForEditor(editorChoice: EditorChoice): ProjectViewNodePath?

  suspend fun findNodeForSelectIn(selectInRequest: SelectInRequest): ProjectViewNodePath?
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
}

internal data class ProjectViewPaneNavigateOptionsImpl(
  override val requestFocus: Boolean,
) : ProjectViewPaneNavigateOptions
