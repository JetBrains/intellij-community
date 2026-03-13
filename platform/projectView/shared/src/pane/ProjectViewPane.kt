// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneId : Comparable<ProjectViewPaneId> {
  companion object {
    val DATA_KEY: DataKey<ProjectViewPaneId> = DataKey.create("ProjectViewPaneId")
  }

  val idString: @NonNls String

  override fun compareTo(other: ProjectViewPaneId): Int = idString.compareTo(other.idString)
}

@ApiStatus.Internal
@Serializable
data class ProjectViewPaneDescriptor(
  val id: ProjectViewPaneId,
  val presentableName: @NonNls String,
  val order: Int,
  val isDefault: Boolean,
)

@ApiStatus.Internal
val PROJECT_VIEW_SELECTED_NODE_IDS_KEY: DataKey<List<Long>> = DataKey.create("ProjectViewSelectedNodeIds")

@ApiStatus.Internal
fun projectViewPaneId(idString: @NonNls String): ProjectViewPaneId = ProjectViewPaneIdImpl(idString)

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewNodePath {
  val paneId: ProjectViewPaneId
  val nodeIds: List<Long>
}

@ApiStatus.Internal
fun projectViewNodePath(paneId: ProjectViewPaneId, nodeIds: List<Long>): ProjectViewNodePath = ProjectViewNodePathImpl(paneId, nodeIds)

internal class ProjectViewPaneIdDataContextSerializer : CustomDataContextSerializer<ProjectViewPaneId> {
  override val key: DataKey<ProjectViewPaneId>
    get() = ProjectViewPaneId.DATA_KEY
  override val serializer: KSerializer<ProjectViewPaneId>
    get() = ProjectViewPaneId.serializer()
}

internal class ProjectViewSelectedNodeIdsDataContextSerializer : CustomDataContextSerializer<List<Long>> {
  override val key: DataKey<List<Long>>
    get() = PROJECT_VIEW_SELECTED_NODE_IDS_KEY
  override val serializer: KSerializer<List<Long>>
    get() = serializer()
}

@Serializable
private data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@Serializable
private data class ProjectViewNodePathImpl(
  override val paneId: ProjectViewPaneId,
  override val nodeIds: List<Long>,
) : ProjectViewNodePath
