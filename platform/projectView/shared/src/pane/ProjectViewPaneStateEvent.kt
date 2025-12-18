// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.treeStructure.TreeNodePresentation
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface ProjectViewPaneStateEvent {
  fun toDTO(): ProjectViewPaneStateEventDTO
}

@ApiStatus.Internal
@Serializable
sealed interface ProjectViewPaneStateEventDTO {
  fun toEvent(): ProjectViewPaneStateEvent
}

@ApiStatus.Internal
@Serializable
sealed class ProjectViewPaneStateSerializableEvent : ProjectViewPaneStateEvent, ProjectViewPaneStateEventDTO {
  override fun toDTO(): ProjectViewPaneStateEventDTO = this

  override fun toEvent(): ProjectViewPaneStateEvent = this
}

@ApiStatus.Internal
data class ProjectViewNodeAdded(
  val parentId: Long,
  val index: Int,
  val nodeId: Long,
  val presentation: TreeNodePresentation,
) : ProjectViewPaneStateEvent {
  override fun toDTO(): ProjectViewPaneStateEventDTO = ProjectViewNodeAddedDTO(
    parentId, index, nodeId, presentation.toDTO()
  )
}

@ApiStatus.Internal
@Serializable
data class ProjectViewNodeAddedDTO(
  val parentId: Long,
  val index: Int,
  val nodeId: Long,
  val presentationDTO: TreeNodePresentationDTO,
) : ProjectViewPaneStateEventDTO {
  override fun toEvent(): ProjectViewPaneStateEvent = ProjectViewNodeAdded(
    parentId, index, nodeId, presentationDTO.toPresentation()
  )
}

@ApiStatus.Internal
@Serializable
data class ProjectViewChildRemoved(
  val parentId: Long,
  val index: Int,
) : ProjectViewPaneStateSerializableEvent()

@ApiStatus.Internal
@Serializable
data class ProjectViewChildrenRemoved(val parentId: Long) : ProjectViewPaneStateSerializableEvent()

@ApiStatus.Internal
data class ProjectViewNodeUpdated(
  val nodeId: Long,
  val presentation: TreeNodePresentation,
) : ProjectViewPaneStateEvent {
  override fun toDTO(): ProjectViewPaneStateEventDTO = ProjectViewNodeUpdatedDTO(
    nodeId, presentation.toDTO()
  )
}

@ApiStatus.Internal
@Serializable
data class ProjectViewNodeUpdatedDTO(
  val nodeId: Long,
  val presentationDTO: TreeNodePresentationDTO,
) : ProjectViewPaneStateEventDTO {
  override fun toEvent(): ProjectViewPaneStateEvent = ProjectViewNodeUpdated(
    nodeId,
    presentationDTO.toPresentation(),
  )
}
