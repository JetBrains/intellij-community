// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import kotlinx.serialization.Serializable

@Serializable
internal data class ProjectViewNodeModelDTO(
  val id: Long,
  val presentationDTO: TreeNodePresentationDTO,
  val flags: Int,
)

internal fun ProjectViewNodeModelImpl<*>.toDTO(): ProjectViewNodeModelDTO = ProjectViewNodeModelDTO(
  id = id,
  presentationDTO = presentation.toDTO(),
  flags = flags,
)

internal fun ProjectViewNodeModelDTO.toModel(): ProjectViewNodeModelImpl<*> = ProjectViewNodeModelImpl(
  maybeUserObject = null,
  id = id,
  presentation = presentationDTO.toPresentation() as TreeNodePresentationImpl,
  flags = flags,
)
