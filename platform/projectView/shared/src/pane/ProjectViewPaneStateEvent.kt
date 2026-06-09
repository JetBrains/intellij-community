// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.platform.projectView.actions.ProjectViewActionState
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
@Serializable
data object ProjectViewClearStateEvent : ProjectViewPaneStateSerializableEvent()

@ApiStatus.Internal
@Serializable
data class ProjectViewActionStateEvent(
  val actionState: ProjectViewActionState,
) : ProjectViewPaneStateSerializableEvent()

@ApiStatus.Internal
data class ProjectViewChildrenLoaded(
  val parentId: Long,
  val children: List<ProjectViewNodeModel>,
) : ProjectViewPaneStateEvent {
  override fun toDTO(): ProjectViewPaneStateEventDTO = ProjectViewChildrenLoadedDTO(
    parentId, children.map { it.toDTO() }
  )
}

@ApiStatus.Internal
data class ProjectViewNodeAdded(
  val parentId: Long,
  val index: Int,
  val model: ProjectViewNodeModel,
) : ProjectViewPaneStateEvent {
  override fun toDTO(): ProjectViewPaneStateEventDTO = ProjectViewNodeAddedDTO(
    parentId, index, model.toDTO()
  )
}

@Serializable
internal data class ProjectViewChildrenLoadedDTO(
  val parentId: Long,
  val childrenDTO: List<ProjectViewNodeModelDTO>,
) : ProjectViewPaneStateEventDTO {
  override fun toEvent(): ProjectViewPaneStateEvent = ProjectViewChildrenLoaded(
    parentId, childrenDTO.map { it.toModel() }
  )
}

@Serializable
internal data class ProjectViewNodeAddedDTO(
  val parentId: Long,
  val index: Int,
  val modelDTO: ProjectViewNodeModelDTO,
) : ProjectViewPaneStateEventDTO {
  override fun toEvent(): ProjectViewPaneStateEvent = ProjectViewNodeAdded(
    parentId, index, modelDTO.toModel()
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
  val model: ProjectViewNodeModel,
) : ProjectViewPaneStateEvent {
  override fun toDTO(): ProjectViewPaneStateEventDTO = ProjectViewNodeUpdatedDTO(
    model.toDTO()
  )
}

@Serializable
internal data class ProjectViewNodeUpdatedDTO(
  val modelDTO: ProjectViewNodeModelDTO,
) : ProjectViewPaneStateEventDTO {
  override fun toEvent(): ProjectViewPaneStateEvent = ProjectViewNodeUpdated(
    modelDTO.toModel(),
  )
}
