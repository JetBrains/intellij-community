// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.vcs.changes.ChangeListManagerState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface ChangeListsApi : RemoteApi<Unit> {
  suspend fun areChangeListsEnabled(projectId: ProjectId): Flow<Boolean>

  suspend fun getChangeListManagerState(projectId: ProjectId): Flow<ChangeListManagerState>

  suspend fun getChangeLists(projectId: ProjectId): Flow<List<ChangeListDto>>

  suspend fun getUnversionedFiles(projectId: ProjectId): Flow<List<FilePathDto>>

  suspend fun getIgnoredFiles(projectId: ProjectId): Flow<List<FilePathDto>>

  suspend fun moveChanges(projectId: ProjectId, changes: List<ChangeId>, changeListId: String)

  suspend fun addUnversionedFiles(projectId: ProjectId, files: List<FilePathDto>, changeListId: String)

  companion object {
    suspend fun getInstance(): ChangeListsApi = RemoteApiProviderService.Companion.resolve(remoteApiDescriptor<ChangeListsApi>())
  }
}