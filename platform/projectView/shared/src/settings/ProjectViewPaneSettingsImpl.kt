// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.platform.projectView.actions.toDTO
import org.jetbrains.annotations.ApiStatus

internal data object ProjectViewPaneSortByNameImpl : ProjectViewPaneSortByName
internal data object ProjectViewPaneSortByTypeImpl : ProjectViewPaneSortByType
internal data class ProjectViewPaneSortByTimeImpl(override val isAscending: Boolean) : ProjectViewPaneSortByTime

internal data class ProjectViewPaneFileNestingValueImpl(
  override val isFileNestingOn: Boolean,
  override val nestingRules: List<ProjectViewFileNestingService.NestingRule>,
) : ProjectViewPaneFileNestingValue

@ApiStatus.Internal
fun NodeSortKey.toSettingValue(): ProjectViewPaneSortKey {
  return when (this) {
    NodeSortKey.BY_NAME -> ProjectViewPaneSortKey.byName()
    NodeSortKey.BY_TYPE -> ProjectViewPaneSortKey.byType()
    NodeSortKey.BY_TIME_ASCENDING -> ProjectViewPaneSortKey.byTime(isAscending = true)
    NodeSortKey.BY_TIME_DESCENDING -> ProjectViewPaneSortKey.byTime(isAscending = false)
  }
}

@ApiStatus.Internal
fun ProjectViewPaneSortKey.toLegacySortKey(): NodeSortKey {
  return when (this) {
    is ProjectViewPaneSortByName -> NodeSortKey.BY_NAME
    is ProjectViewPaneSortByType -> NodeSortKey.BY_TYPE
    is ProjectViewPaneSortByTime -> if (isAscending) NodeSortKey.BY_TIME_ASCENDING else NodeSortKey.BY_TIME_DESCENDING
    else -> {
      LOG.warn("Unsupported sort key: $this")
      NodeSortKey.BY_NAME
    }
  }
}

internal class ProjectViewPaneSettingsStateBuilderImpl : ProjectViewPaneSettingsStateBuilder {
  private val optionState = hashMapOf<ProjectViewPaneOptionDTO, ProjectViewOptionStateDTO>()
  private var sortKey = NodeSortKey.BY_NAME
  private var availableSortKeys = NodeSortKey.entries.toList()
  private var fileNesting = FileNestingStateDTO(
    isFileNestingOn = false,
    isFileNestingAvailable = false,
    activeRules = emptyList(),
    defaultRules = emptyList(),
  )

  override fun setOptionState(
    option: ProjectViewPaneOption,
    isSelected: Boolean,
    isEnabled: Boolean,
    isAlwaysVisible: Boolean,
  ) {
    optionState[option.toDTO()] = ProjectViewOptionStateDTO(isSelected, isEnabled, isAlwaysVisible)
  }

  override fun setAvailableSortKeys(sortKeys: List<ProjectViewPaneSortKey>) {
    this.availableSortKeys = sortKeys.map { it.toLegacySortKey() }
  }

  override fun setSortKey(sortKey: ProjectViewPaneSortKey) {
    this.sortKey = sortKey.toLegacySortKey()
  }

  override fun setFileNesting(
    isFileNestingOn: Boolean,
    isFileNestingAvailable: Boolean,
    activeRules: List<ProjectViewFileNestingService.NestingRule>,
    defaultRules: List<ProjectViewFileNestingService.NestingRule>,
  ) {
    fileNesting = FileNestingStateDTO(
      isFileNestingOn, isFileNestingAvailable, activeRules.map { it.toDTO() }, defaultRules.map { it.toDTO() }
    )
  }

  fun build(): ProjectViewPaneSettingsStateDTO {
    return ProjectViewPaneSettingsStateDTO(
      optionState,
      ProjectViewSortKeyStateDTO(sortKey, availableSortKeys),
      fileNesting,
    )
  }
}

private val LOG = fileLogger()
