// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.VcsKey
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface VcsMappingsApi : RemoteApi<Unit> {
  suspend fun getMappings(projectId: ProjectId): Flow<VcsMappingsDto>

  companion object {
    suspend fun getInstance(): VcsMappingsApi = RemoteApiProviderService.resolve(remoteApiDescriptor<VcsMappingsApi>())
  }
}

@Serializable
@ApiStatus.Internal
data class VcsMappingsDto(val mappings: List<VcsMappingDto>)

@Serializable
@ApiStatus.Internal
data class VcsMappingDto(
  val root: FilePathDto,
  val vcs: VcsKey?,
)