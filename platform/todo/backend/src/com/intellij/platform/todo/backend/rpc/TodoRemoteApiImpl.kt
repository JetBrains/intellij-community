// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.todo.rpc.TodoFilesWatchRequest
import com.intellij.ide.todo.rpc.TodoFilterConfig
import com.intellij.ide.todo.rpc.TodoPatternConfig
import com.intellij.ide.todo.rpc.TodoQuerySettings
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.todo.backend.model.BackendTodoModel
import com.intellij.platform.todo.backend.model.TodoFileResultBuilder.buildTodoFileResult
import com.intellij.platform.todo.backend.model.TodoFileResultBuilder.collectTodoResults
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {

  override fun watchTodoFiles(
    projectId: ProjectId,
    request: TodoFilesWatchRequest,
  ): Flow<TodoEvent> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val filter = resolveFilter(project, request.filter)
    coroutineScope {
      val backendModel = BackendTodoModel(project, this, request.scope, filter)
      backendModel.events.collect { event ->
        send(event)
      }
    }
  }

  override fun listTodoFiles(
    projectId: ProjectId,
    filter: TodoFilterConfig?,
  ): Flow<TodoFileResult> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val resolvedFilter = resolveFilter(project, filter)

    readAction {
      blockingContextToIndicator {
        val helper = PsiTodoSearchHelper.getInstance(project)

        helper.processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
          val result = buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter) ?: return@processFilesWithTodoItems true
          trySend(result).isSuccess
        }
      }
    }
  }.buffer(Channel.UNLIMITED)

  override fun listTodos(
    projectId: ProjectId,
    settings: TodoQuerySettings
  ): Flow<TodoResult> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val virtualFile = settings.fileId.virtualFile() ?: return@channelFlow
    val filter = resolveFilter(project, settings.filter)

    val results: List<TodoResult> = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction emptyList()
      collectTodoResults(project, psiFile, virtualFile, filter)
    }

    for (result in results) {
      send(result)
    }
  }

  override fun getFilesWithTodos(
    projectId: ProjectId,
    filter: TodoFilterConfig?,
  ): Flow<VirtualFileId> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val resolvedFilter = resolveFilter(project, filter)

    readAction {
      blockingContextToIndicator {
        val helper = PsiTodoSearchHelper.getInstance(project)

        helper.processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true

          val matchesFilter = if (resolvedFilter != null) {
            resolvedFilter.accept(helper, psiFile)
          } else {
            helper.getTodoItemsCount(psiFile) > 0
          }

          if (!matchesFilter) {
            return@processFilesWithTodoItems true
          }
          trySend(virtualFile.rpcId()).isSuccess
        }
      }
    }
  }.buffer(Channel.UNLIMITED)

  override suspend fun getTodoCount(
    projectId: ProjectId,
    fileId: VirtualFileId,
    filter: TodoFilterConfig?,
  ): Int {
    val project = projectId.findProjectOrNull() ?: return 0
    val virtualFile = fileId.virtualFile() ?: return 0
    val resolvedFilter = resolveFilter(project, filter)

    return readAction {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction 0
      val helper = PsiTodoSearchHelper.getInstance(project)

      if (resolvedFilter != null) {
        val items = helper.findTodoItems(psiFile)
        items.count { item -> resolvedFilter.contains(item.pattern)}
      } else {
        helper.getTodoItemsCount(psiFile)
      }
    }
  }

  override suspend fun fileMatchesFilter(
    projectId: ProjectId,
    fileId: VirtualFileId,
    filter: TodoFilterConfig?
  ): Boolean {
    val project = projectId.findProjectOrNull() ?: return false
    val virtualFile = fileId.virtualFile() ?: return false
    val resolvedFilter = resolveFilter(project, filter)

    return readAction {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction false
      val helper = PsiTodoSearchHelper.getInstance(project)

      if (resolvedFilter != null) {
        resolvedFilter.accept(helper, psiFile)
      } else {
        helper.getTodoItemsCount(psiFile) > 0
      }
    }
  }

  private fun resolveFilter(project: Project, config: TodoFilterConfig?): TodoFilter? {
    if (config == null) return null

    config.name?.let { name ->
      val byName = TodoConfiguration.getInstance().getTodoFilter(name)
      if (byName != null) return byName
    }

    if (config.patterns.isEmpty()) return null

    return TodoFilter().apply {
      config.patterns.forEach { config: TodoPatternConfig ->
        val patternString = config.pattern
        val pattern = TodoPattern(patternString, TodoAttributesUtil.createDefault(), config.isCaseSensitive)
        addTodoPattern(pattern)
      }
    }
  }
}