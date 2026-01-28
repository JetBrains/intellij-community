// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.ui.tree.TreeNodePresentationBuilderImpl
import com.intellij.ui.treeStructure.TreeNodePresentation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneProviderId {
  companion object {
    val DATA_KEY: DataKey<ProjectViewPaneProviderId> = DataKey.create("ProjectViewPaneProviderId")
  }

  val idString: @NonNls String
}

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneId {
  companion object {
    val DATA_KEY: DataKey<ProjectViewPaneId> = DataKey.create("ProjectViewPaneId")
  }

  val idString: @NonNls String
}

@ApiStatus.Internal
val PROJECT_VIEW_SELECTED_NODE_IDS_KEY: DataKey<List<Long>> = DataKey.create("ProjectViewSelectedNodeIds")

@ApiStatus.Internal
fun projectViewPaneProviderId(idString: @NonNls String): ProjectViewPaneProviderId = ProjectViewPaneProviderIdImpl(idString)

@ApiStatus.Internal
fun projectViewPaneId(idString: @NonNls String): ProjectViewPaneId = ProjectViewPaneIdImpl(idString)

internal class ProjectViewPaneProviderIdDataContextSerializer : CustomDataContextSerializer<ProjectViewPaneProviderId> {
  override val key: DataKey<ProjectViewPaneProviderId>
    get() = ProjectViewPaneProviderId.DATA_KEY
  override val serializer: KSerializer<ProjectViewPaneProviderId>
    get() = ProjectViewPaneProviderId.serializer()
}

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
private data class ProjectViewPaneProviderIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneProviderId

@Serializable
private data class ProjectViewPaneIdImpl(
  override val idString: @NonNls String
) : ProjectViewPaneId

@ApiStatus.Internal
const val SUPER_ROOT_ID: Long = 0L

@ApiStatus.Internal
object SuperRoot

@ApiStatus.Internal
val SuperRootPresentation: TreeNodePresentation = TreeNodePresentationBuilderImpl(false).also {
  it.setMainText("fake root - for convenience, not to display")
}.build()
