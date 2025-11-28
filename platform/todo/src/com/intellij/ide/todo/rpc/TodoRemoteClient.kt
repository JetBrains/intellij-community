// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import kotlinx.coroutines.flow.toList
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class TodoRemoteClient {

  companion object {

    @JvmStatic
    fun findAllTodos(
      project: Project,
      file: VirtualFile,
      filter: TodoFilter?
    ) : List<TodoResult> = runBlockingCancellable {
      val projectId: ProjectId = project.projectId()
      val settings = TodoQuerySettings(file.rpcId(), filter?.let { toConfig(it) })
      TodoRemoteApi.getInstance().listTodos(projectId, settings).toList()
    }

    @JvmStatic
    internal fun toConfig(filter: TodoFilter): TodoFilterConfig {
      val patterns = mutableListOf<TodoPatternConfig>()
      val it = filter.iterator()
      while (it.hasNext()) {
        val filter = it.next()
        patterns.add(TodoPatternConfig(filter.patternString, filter.isCaseSensitive))
      }
      return TodoFilterConfig(filter.name, patterns)
    }
  }
}