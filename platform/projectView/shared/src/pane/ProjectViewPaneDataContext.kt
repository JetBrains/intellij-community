@file:ApiStatus.Internal
// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.CustomDataContextSerializer
import com.intellij.openapi.actionSystem.DataKey
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
val PROJECT_VIEW_SELECTED_NODE_IDS_KEY: DataKey<List<Long>> = DataKey.create("ProjectViewSelectedNodeIds")

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
