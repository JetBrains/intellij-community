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
import fleet.rpc.client.durable
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly

@Internal
@Rpc
interface StructureTreeApi : RemoteApi<Unit> {
  suspend fun getShowPopupRequestFlow(): Flow<ShowStructurePopupRequest>

  suspend fun createStructureViewModel(projectId: ProjectId, id: StructureViewDtoId, fileEditorId: FileEditorId, fileId: VirtualFileId): StructureViewModelDto?

  suspend fun structureViewModelDisposed(projectId: ProjectId, id: StructureViewDtoId)

  suspend fun setTreeActionState(projectId: ProjectId, id: StructureViewDtoId, actionName: String, isEnabled: Boolean, autoClicked: Boolean)

  @TestOnly
  suspend fun getNewSelection(projectId: ProjectId, id: StructureViewDtoId): Int?

  suspend fun navigateToElement(projectId: ProjectId, id: StructureViewDtoId, elementId: Int): Boolean

  companion object {
    suspend fun getInstance(): StructureTreeApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<StructureTreeApi>())
    }

    suspend fun callDisposeModel(projectId: ProjectId, id: StructureViewDtoId) {
      durable {
        getInstance().structureViewModelDisposed(projectId, id)
      }
    }
  }
}