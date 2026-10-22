// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ui.treeStructure.TreeNodePresentationImpl
import kotlinx.serialization.Serializable

@Serializable
internal data class ProjectViewNodeModelDTO(
  val id: Long,
  val presentationDTO: TreeNodePresentationDTO,
  val userObjectDTO: ProjectViewNodeUserObjectDTO,
  val pathElementType: String,
  val pathElementId: String,
  val flags: Int,
)

@Serializable
internal data class ProjectViewNodeUserObjectDTO(
  val name: String, // not used on the frontend currently, but is useful for debugging
  val virtualFileId: VirtualFileId?,
)

internal fun ProjectViewNodeModelImpl<*>.toDTO(): ProjectViewNodeModelDTO = ProjectViewNodeModelDTO(
  id = id,
  presentationDTO = presentation.toDTO(),
  userObjectDTO = userObject.toDTO(),
  pathElementType = pathElementType,
  pathElementId = pathElementId,
  flags = flags,
)

private fun ProjectViewNodeUserObject.toDTO(): ProjectViewNodeUserObjectDTO {
  return ProjectViewNodeUserObjectDTO(
    name = name,
    virtualFileId = getVirtualFile()?.rpcId(),
  )
}

private fun ProjectViewNodeUserObjectDTO.toFrontendUserObject(): ProjectViewNodeUserObject {
  return FrontendProjectViewNodeUserObject(
    name = name,
    virtualFileId = virtualFileId,
  )
}

internal fun ProjectViewNodeModelDTO.toModel(): ProjectViewNodeModelImpl<ProjectViewNodeUserObject> = ProjectViewNodeModelImpl(
  userObject = userObjectDTO.toFrontendUserObject(),
  id = id,
  presentation = presentationDTO.toPresentation() as TreeNodePresentationImpl,
  pathElementType = pathElementType,
  pathElementId = pathElementId,
  flags = flags,
)
