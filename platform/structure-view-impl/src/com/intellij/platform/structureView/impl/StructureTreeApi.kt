// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.structureView.impl

import com.intellij.ide.rpc.FileEditorId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.structureView.impl.dto.StructureViewDtoId
import com.intellij.platform.structureView.impl.dto.StructureViewModelDto
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@Internal
@Rpc
interface StructureTreeApi : RemoteApi<Unit> {
  suspend fun createStructureViewModel(id: StructureViewDtoId, fileEditorId: FileEditorId, fileId: VirtualFileId, projectId: ProjectId): StructureViewModelDto?

  suspend fun structureViewModelDisposed(id: StructureViewDtoId)

  suspend fun setTreeActionState(id: StructureViewDtoId, actionName: String, isEnabled: Boolean, autoClicked: Boolean)

  @TestOnly
  suspend fun getNewSelection(id: StructureViewDtoId): Int?

  suspend fun navigateToElement(id: StructureViewDtoId, elementId: Int): Boolean

  companion object {
    suspend fun getInstance(): StructureTreeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<StructureTreeApi>())
    }
  }
}