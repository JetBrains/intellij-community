// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.projectView.pane

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.vfs.VirtualFile
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
  val virtualFileId: VirtualFileId?,
) : ProjectViewNodeUserObject {
  override fun getVirtualFile(): VirtualFile? = virtualFileId?.virtualFile()
}

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
    getVirtualFile()?.rpcId(),
  )
}

internal fun ProjectViewNodeModelDTO.toModel(): ProjectViewNodeModelImpl<ProjectViewNodeUserObject> = ProjectViewNodeModelImpl(
  userObject = userObjectDTO,
  id = id,
  presentation = presentationDTO.toPresentation() as TreeNodePresentationImpl,
  pathElementType = pathElementType,
  pathElementId = pathElementId,
  flags = flags,
)
