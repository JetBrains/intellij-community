// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface TodoRemoteApi : RemoteApi<Unit> {
  suspend fun listTodos(
    projectId: ProjectId,
    settings: TodoQuerySettings,
  ): Flow<TodoResult>

  companion object {
    @JvmStatic
    suspend fun getInstance(): TodoRemoteApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<TodoRemoteApi>())
    }
  }
}