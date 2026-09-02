@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.platform.projectView.actions.fromDTO
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneOption {
  data object OpenInPreviewTab : ProjectViewPaneOption
  data object AutoscrollToSource : ProjectViewPaneOption
  data object OpenDirectoriesWithSingleClick : ProjectViewPaneOption
  data object AutoscrollFromSource : ProjectViewPaneOption
  data object ShowModules : ProjectViewPaneOption
  data object ShowMembers : ProjectViewPaneOption
  data object ShowExcludedFiles : ProjectViewPaneOption
  data object ShowVisibilityIcons : ProjectViewPaneOption
  data object ShowLibraryContents : ProjectViewPaneOption
  data object ShowScratchesAndConsoles : ProjectViewPaneOption
  data object FlattenModules : ProjectViewPaneOption
  data object FlattenPackages : ProjectViewPaneOption
  data object AbbreviatePackageNames : ProjectViewPaneOption
  data object HideEmptyMiddlePackages : ProjectViewPaneOption
  data object CompactDirectories : ProjectViewPaneOption
  data object FoldersAlwaysOnTop : ProjectViewPaneOption
  data object ManualOrder : ProjectViewPaneOption
}

@ApiStatus.Experimental
fun allProjectViewPaneOptions(): List<ProjectViewPaneOption> {
  return ProjectViewPaneOptionDTO.entries.map { it.fromDTO() }
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortKey {
  companion object {
    fun byName(): ProjectViewPaneSortByName = ProjectViewPaneSortByNameImpl
    fun byType(): ProjectViewPaneSortByType = ProjectViewPaneSortByTypeImpl
    fun byTime(isAscending: Boolean): ProjectViewPaneSortByTime = ProjectViewPaneSortByTimeImpl(isAscending)
  }
}

@ApiStatus.Experimental
fun allProjectViewPaneSortKeys(): List<ProjectViewPaneSortKey> {
  return NodeSortKey.entries.map { it.toSettingValue() }
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByName : ProjectViewPaneSortKey
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByType : ProjectViewPaneSortKey
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByTime : ProjectViewPaneSortKey {
  val isAscending: Boolean
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneFileNestingValue {
  val isFileNestingOn: Boolean
  val nestingRules: List<ProjectViewFileNestingService.NestingRule>
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSettingsStateBuilder {
  fun setOptionState(option: ProjectViewPaneOption, isSelected: Boolean, isEnabled: Boolean, isAlwaysVisible: Boolean)
  fun setSortKey(sortKey: ProjectViewPaneSortKey)
  fun setAvailableSortKeys(sortKeys: List<ProjectViewPaneSortKey>)
  fun setFileNesting(
    isFileNestingOn: Boolean,
    isFileNestingAvailable: Boolean,
    activeRules: List<ProjectViewFileNestingService.NestingRule>,
    defaultRules: List<ProjectViewFileNestingService.NestingRule>,
  )
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSettingsAccessor {
  fun isOptionSelected(option: ProjectViewPaneOption): Boolean
  fun getSortKey(): ProjectViewPaneSortKey
  fun getFileNesting(): ProjectViewPaneFileNestingValue
}
