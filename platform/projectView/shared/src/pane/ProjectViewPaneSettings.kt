// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSetting<T : Any> {
  val newValue: T
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewOptionSetting : ProjectViewPaneSetting<Boolean> {
  @ApiStatus.NonExtendable interface OpenInPreviewTab : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface AutoscrollToSource : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface OpenDirectoriesWithSingleClick : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface AutoscrollFromSource : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowModules : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowMembers : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowExcludedFiles : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowVisibilityIcons : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowLibraryContents : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ShowScratchesAndConsoles : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface FlattenModules : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface FlattenPackages : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface AbbreviatePackageNames : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface HideEmptyMiddlePackages : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface CompactDirectories : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface FoldersAlwaysOnTop : ProjectViewOptionSetting
  @ApiStatus.NonExtendable interface ManualOrder : ProjectViewOptionSetting
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewSortKeySetting : ProjectViewPaneSetting<ProjectViewPaneSortKeyValue>

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortKeyValue
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByName : ProjectViewPaneSortKeyValue
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByType : ProjectViewPaneSortKeyValue
@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneSortByTime : ProjectViewPaneSortKeyValue {
  val isAscending: Boolean
}

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneFileNestingSetting : ProjectViewPaneSetting<ProjectViewPaneFileNestingValue>

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface ProjectViewPaneFileNestingValue {
  val isFileNestingOn: Boolean
  val nestingRules: List<ProjectViewFileNestingService.NestingRule>
}
