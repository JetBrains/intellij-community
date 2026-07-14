// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.settings

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneSettingsStateDTO(
  val optionStates: Map<ProjectViewPaneOptionDTO, ProjectViewOptionStateDTO>,
  val sortKeyState: ProjectViewSortKeyStateDTO,
  val fileNestingState: FileNestingStateDTO,
)

@ApiStatus.Internal
@Serializable
data class ProjectViewOptionStateDTO(
  val isSelected: Boolean,
  val isEnabled: Boolean,
  val isAlwaysVisible: Boolean,
)

@ApiStatus.Internal
enum class ProjectViewPaneOptionDTO {
  OPEN_IN_PREVIEW_TAB,
  AUTOSCROLL_TO_SOURCE,
  OPEN_DIRECTORIES_WITH_SINGLE_CLICK,
  AUTOSCROLL_FROM_SOURCE,
  SHOW_MODULES,
  SHOW_MEMBERS,
  SHOW_EXCLUDED_FILES,
  SHOW_VISIBILITY_ICONS,
  SHOW_LIBRARY_CONTENTS,
  SHOW_SCRATCHES_AND_CONSOLES,
  FLATTEN_MODULES,
  FLATTEN_PACKAGES,
  ABBREVIATE_PACKAGE_NAMES,
  HIDE_EMPTY_MIDDLE_PACKAGES,
  COMPACT_DIRECTORIES,
  FOLDERS_ALWAYS_ON_TOP,
  MANUAL_ORDER,
}

@ApiStatus.Internal
@Serializable
data class ProjectViewSortKeyStateDTO(
    val sortKey: NodeSortKey,
    val availableSortKeys: List<NodeSortKey>,
)

@ApiStatus.Internal
@Serializable
data class FileNestingStateDTO(
  val isFileNestingOn: Boolean,
  val isFileNestingAvailable: Boolean,
  val activeRules: List<NestingRuleDTO>,
  val defaultRules: List<NestingRuleDTO>,
)

@ApiStatus.Internal
@Serializable
data class NestingRuleDTO(
  val parentFileSuffix: String,
  val childFileSuffix: String,
) {
  fun toNestingRule(): ProjectViewFileNestingService.NestingRule = ProjectViewFileNestingService.NestingRule(parentFileSuffix, childFileSuffix)
}

@ApiStatus.Internal
fun ProjectViewFileNestingService.NestingRule.toDTO(): NestingRuleDTO = NestingRuleDTO(parentFileSuffix, childFileSuffix)
