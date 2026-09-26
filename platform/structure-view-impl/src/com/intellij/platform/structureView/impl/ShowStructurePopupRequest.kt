// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.project.ProjectId
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import fleet.rpc.core.SendChannelSerializer
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class ShowStructurePopupRequest(
  val projectId: ProjectId,
  val modelId: StructureViewDtoId,
  val model: StructureViewModelDto,
  val fileId: VirtualFileId?,
  val title: @NlsContexts.PopupTitle String?,
  @Serializable(with = SendChannelSerializer::class) val received: SendChannel<Unit>,
)
