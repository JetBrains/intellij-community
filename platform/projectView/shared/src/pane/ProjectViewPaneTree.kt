// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Experimental
sealed interface ProjectViewNodePath {
  val paneId: ProjectViewPaneId
  val nodeIds: List<Long>
}

@ApiStatus.Internal
fun projectViewNodePath(paneId: ProjectViewPaneId, nodeIds: List<Long>): ProjectViewNodePath = ProjectViewNodePathImpl(paneId, nodeIds)

@ApiStatus.Internal
@Serializable
data class ProjectViewNodePathImpl(
  override val paneId: ProjectViewPaneId,
  override val nodeIds: List<Long>,
) : ProjectViewNodePath
