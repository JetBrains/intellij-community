// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.rpc.TodoQuerySettings
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.platform.project.ProjectId
import kotlinx.coroutines.flow.Flow

internal class TodoRemoteApiImpl : TodoRemoteApi {
  override suspend fun listTodos(projectId: ProjectId, settings: TodoQuerySettings): Flow<TodoResult> {
    TODO("Not yet implemented")
  }
}