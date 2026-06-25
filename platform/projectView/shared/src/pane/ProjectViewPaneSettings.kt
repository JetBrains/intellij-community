// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
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
interface ProjectViewPaneFileNestingValue {
  val isFileNestingOn: Boolean
  val nestingRules: List<ProjectViewFileNestingService.NestingRule>
}
