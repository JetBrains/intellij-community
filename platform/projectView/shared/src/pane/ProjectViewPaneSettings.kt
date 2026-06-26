@file:ApiStatus.Experimental
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.platform.projectView.actions.fromDTO
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneOption {
  sealed interface OpenInPreviewTab : ProjectViewPaneOption
  sealed interface AutoscrollToSource : ProjectViewPaneOption
  sealed interface OpenDirectoriesWithSingleClick : ProjectViewPaneOption
  sealed interface AutoscrollFromSource : ProjectViewPaneOption
  sealed interface ShowModules : ProjectViewPaneOption
  sealed interface ShowMembers : ProjectViewPaneOption
  sealed interface ShowExcludedFiles : ProjectViewPaneOption
  sealed interface ShowVisibilityIcons : ProjectViewPaneOption
  sealed interface ShowLibraryContents : ProjectViewPaneOption
  sealed interface ShowScratchesAndConsoles : ProjectViewPaneOption
  sealed interface FlattenModules : ProjectViewPaneOption
  sealed interface FlattenPackages : ProjectViewPaneOption
  sealed interface AbbreviatePackageNames : ProjectViewPaneOption
  sealed interface HideEmptyMiddlePackages : ProjectViewPaneOption
  sealed interface CompactDirectories : ProjectViewPaneOption
  sealed interface FoldersAlwaysOnTop : ProjectViewPaneOption
  sealed interface ManualOrder : ProjectViewPaneOption
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
