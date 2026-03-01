// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun findAllTodos(
  project: Project,
  file: VirtualFile,
  filter: TodoFilter?
): List<TodoResult> = runBlockingCancellable {
  durable {
    val projectId: ProjectId = project.projectId()
    val settings = TodoQuerySettings(file.rpcId(), filter?.let { toConfig(it) })
    TodoRemoteApi.getInstance().listTodos(projectId, settings).toList()
  }
}

@ApiStatus.Internal
suspend fun getFilesWithTodos(
  project: Project,
  filter: TodoFilter?
): Flow<VirtualFile> = durable {
  val projectId: ProjectId = project.projectId()
  TodoRemoteApi.getInstance().getFilesWithTodos(projectId, filter?.let { toConfig(it) })
    .mapNotNull { it.virtualFile() }
}

@ApiStatus.Internal
fun getTodoCount(
  project: Project,
  file: VirtualFile,
  filter: TodoFilter?
): Int = runBlockingCancellable {
  durable {
    val projectId: ProjectId = project.projectId()
    TodoRemoteApi.getInstance().getTodoCount(projectId, file.rpcId(), filter?.let { toConfig(it) })
  }
}

@ApiStatus.Internal
fun fileMatchesFilter(
  project: Project,
  file: VirtualFile,
  filter: TodoFilter?
): Boolean = runBlockingCancellable {
  durable {
    val projectId = project.projectId()
    TodoRemoteApi.getInstance().fileMatchesFilter(projectId, file.rpcId(), filter?.let { toConfig(it) })
  }
}

@ApiStatus.Internal
internal fun toConfig(filter: TodoFilter): TodoFilterConfig {
  val patterns = mutableListOf<TodoPatternConfig>()
  val it = filter.iterator()
  while (it.hasNext()) {
    val filter = it.next()
    patterns.add(TodoPatternConfig(filter.patternString, filter.isCaseSensitive))
  }
  return TodoFilterConfig(filter.name, patterns)
}