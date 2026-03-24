// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.ide.rpc.FileEditorId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val SHOW_STRUCTURE_POPUP_REMOTE_TOPIC: ProjectRemoteTopic<ShowStructurePopupRequest> =
  ProjectRemoteTopic("structureView.show.popup", ShowStructurePopupRequest.serializer())

@ApiStatus.Internal
@Serializable
data class ShowStructurePopupRequest(
  val fileEditorId: FileEditorId,
  val fileId: VirtualFileId,
  val title: @NlsContexts.PopupTitle String?,
)
