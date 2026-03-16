// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.openapi.vcs.ex.LocalRange
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
interface PartialChangesApi : RemoteApi<Unit> {
  suspend fun partialChanges(projectId: ProjectId): Flow<PartialChangesEvent>

  companion object {
    suspend fun getInstance(): PartialChangesApi = RemoteApiProviderService.resolve(remoteApiDescriptor<PartialChangesApi>())
  }
}

@ApiStatus.Internal
@Serializable
sealed class PartialChangesEvent {
  @Serializable
  data class TrackerRemoved(val file: FilePathDto) : PartialChangesEvent() {
    override fun toString(): String {
      return "Tracker removed for $file"
    }
  }

  @Serializable
  data class RangesUpdated(val file: FilePathDto, val ranges: List<LocalRange>) : PartialChangesEvent() {
    override fun toString(): String {
      return "Ranges updated for $file: $ranges"
    }
  }
}
