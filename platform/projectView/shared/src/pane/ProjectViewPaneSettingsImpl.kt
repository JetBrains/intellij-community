// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService

internal sealed class ProjectViewPaneOptionImpl(val dto: ProjectViewPaneOptionDTO) : ProjectViewPaneOption {
  data object OpenInPreviewTab : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.OPEN_IN_PREVIEW_TAB), ProjectViewPaneOption.OpenInPreviewTab
  data object AutoscrollToSource : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.AUTOSCROLL_TO_SOURCE), ProjectViewPaneOption.AutoscrollToSource
  data object OpenDirectoriesWithSingleClick : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.OPEN_DIRECTORIES_WITH_SINGLE_CLICK), ProjectViewPaneOption.OpenDirectoriesWithSingleClick
  data object AutoscrollFromSource : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.AUTOSCROLL_FROM_SOURCE), ProjectViewPaneOption.AutoscrollFromSource
  data object ShowModules : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_MODULES), ProjectViewPaneOption.ShowModules
  data object ShowMembers : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_MEMBERS), ProjectViewPaneOption.ShowMembers
  data object ShowExcludedFiles : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_EXCLUDED_FILES), ProjectViewPaneOption.ShowExcludedFiles
  data object ShowVisibilityIcons : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_VISIBILITY_ICONS), ProjectViewPaneOption.ShowVisibilityIcons
  data object ShowLibraryContents : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_LIBRARY_CONTENTS), ProjectViewPaneOption.ShowLibraryContents
  data object ShowScratchesAndConsoles : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.SHOW_SCRATCHES_AND_CONSOLES), ProjectViewPaneOption.ShowScratchesAndConsoles
  data object FlattenModules : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.FLATTEN_MODULES), ProjectViewPaneOption.FlattenModules
  data object FlattenPackages : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.FLATTEN_PACKAGES), ProjectViewPaneOption.FlattenPackages
  data object AbbreviatePackageNames : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.ABBREVIATE_PACKAGE_NAMES), ProjectViewPaneOption.AbbreviatePackageNames
  data object HideEmptyMiddlePackages : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.HIDE_EMPTY_MIDDLE_PACKAGES), ProjectViewPaneOption.HideEmptyMiddlePackages
  data object CompactDirectories : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.COMPACT_DIRECTORIES), ProjectViewPaneOption.CompactDirectories
  data object FoldersAlwaysOnTop : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.FOLDERS_ALWAYS_ON_TOP), ProjectViewPaneOption.FoldersAlwaysOnTop
  data object ManualOrder : ProjectViewPaneOptionImpl(ProjectViewPaneOptionDTO.MANUAL_ORDER), ProjectViewPaneOption.ManualOrder
}

internal data object ProjectViewPaneSortByNameImpl : ProjectViewPaneSortByName
internal data object ProjectViewPaneSortByTypeImpl : ProjectViewPaneSortByType
internal data class ProjectViewPaneSortByTimeImpl(override val isAscending: Boolean) : ProjectViewPaneSortByTime

internal data class ProjectViewPaneFileNestingValueImpl(
  override val isFileNestingOn: Boolean,
  override val nestingRules: List<ProjectViewFileNestingService.NestingRule>,
) : ProjectViewPaneFileNestingValue

internal fun NodeSortKey.toSettingValue(): ProjectViewPaneSortKeyValue {
  return when (this) {
    NodeSortKey.BY_NAME -> ProjectViewPaneSortByNameImpl
    NodeSortKey.BY_TYPE -> ProjectViewPaneSortByTypeImpl
    NodeSortKey.BY_TIME_ASCENDING -> ProjectViewPaneSortByTimeImpl(isAscending = true)
    NodeSortKey.BY_TIME_DESCENDING -> ProjectViewPaneSortByTimeImpl(isAscending = false)
  }
}
