// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService

internal interface ProjectViewOptionSettingImpl : ProjectViewOptionSetting {
  data class OpenInPreviewTabImpl(override val newValue: Boolean) : ProjectViewOptionSetting.OpenInPreviewTab
  data class AutoscrollToSourceImpl(override val newValue: Boolean) : ProjectViewOptionSetting.AutoscrollToSource
  data class OpenDirectoriesWithSingleClickImpl(override val newValue: Boolean) : ProjectViewOptionSetting.OpenDirectoriesWithSingleClick
  data class AutoscrollFromSourceImpl(override val newValue: Boolean) : ProjectViewOptionSetting.AutoscrollFromSource
  data class ShowModulesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowModules
  data class ShowMembersImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowMembers
  data class ShowExcludedFilesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowExcludedFiles
  data class ShowVisibilityIconsImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowVisibilityIcons
  data class ShowLibraryContentsImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowLibraryContents
  data class ShowScratchesAndConsolesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ShowScratchesAndConsoles
  data class FlattenModulesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.FlattenModules
  data class FlattenPackagesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.FlattenPackages
  data class AbbreviatePackageNamesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.AbbreviatePackageNames
  data class HideEmptyMiddlePackagesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.HideEmptyMiddlePackages
  data class CompactDirectoriesImpl(override val newValue: Boolean) : ProjectViewOptionSetting.CompactDirectories
  data class FoldersAlwaysOnTopImpl(override val newValue: Boolean) : ProjectViewOptionSetting.FoldersAlwaysOnTop
  data class ManualOrderImpl(override val newValue: Boolean) : ProjectViewOptionSetting.ManualOrder
}

internal data class ProjectViewSortKeySettingImpl(override val newValue: ProjectViewPaneSortKeyValue) : ProjectViewSortKeySetting

internal data object ProjectViewPaneSortByNameImpl : ProjectViewPaneSortByName
internal data object ProjectViewPaneSortByTypeImpl : ProjectViewPaneSortByType
internal data class ProjectViewPaneSortByTimeImpl(override val isAscending: Boolean) : ProjectViewPaneSortByTime

internal data class ProjectViewPaneFileNestingSettingImpl(
  override val newValue: ProjectViewPaneFileNestingValue
) : ProjectViewPaneFileNestingSetting

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
