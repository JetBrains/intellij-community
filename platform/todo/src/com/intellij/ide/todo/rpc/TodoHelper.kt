// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import fleet.rpc.client.durable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
suspend fun collectWatchedTodoFiles(
  project: Project,
  scope: TodoScope,
  filter: TodoFilter?,
  collector: suspend (TodoEvent) -> Unit,
) {
  durable {
    val projectId: ProjectId = project.projectId()
    val request = TodoFilesWatchRequest(filter?.toConfig(), scope)
    TodoRemoteApi.getInstance().watchTodoFiles(projectId, request).collect { event ->
     collector(event)
    }
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
    TodoRemoteApi.getInstance().fileMatchesFilter(projectId, file.rpcId(), filter?.toConfig())
  }
}

@ApiStatus.Internal
private fun TodoFilter.toConfig(): TodoFilterConfig {
  val filter = this
  val patterns = mutableListOf<TodoPatternConfig>()
  val it = filter.iterator()
  while (it.hasNext()) {
    val filter = it.next()
    patterns.add(TodoPatternConfig(filter.patternString, filter.isCaseSensitive))
  }
  return TodoFilterConfig(filter.name, patterns)
}