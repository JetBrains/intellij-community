// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.projectView.NodeSortKey
import com.intellij.platform.projectView.settings.NestingRuleDTO
import com.intellij.platform.projectView.settings.ProjectViewPaneOptionDTO
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed class ProjectViewPaneRequest

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneSelectionChanged(
  val paneId: ProjectViewPaneId,
) : ProjectViewPaneRequest()

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneLoadChildrenRequest(
  val nodeId: Long,
) : ProjectViewPaneRequest()

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneNavigateRequest(
  val nodeId: Long,
  val requestFocus: Boolean,
) : ProjectViewPaneRequest()

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneChangeOptionValueRequest(
    val option: ProjectViewPaneOptionDTO,
    val newValue: Boolean,
) : ProjectViewPaneRequest()

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneChangeSortKeyRequest(
  val sortKey: NodeSortKey,
) : ProjectViewPaneRequest()

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneChangeFileNestingRequest(
    val isFileNestingOn: Boolean,
    val activeRules: List<NestingRuleDTO>,
) : ProjectViewPaneRequest()
