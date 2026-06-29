// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.TodoFilesWatchRequest
import com.intellij.ide.todo.rpc.TodoFilterConfig
import com.intellij.ide.todo.rpc.TodoPatternConfig
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.shouldUseSplitTodo
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.todo.backend.model.TodoBackendPsiListener
import com.intellij.platform.todo.backend.model.TodoFileResultBuilder.buildTodoFileResult
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import com.intellij.util.asDisposable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {

  override fun watchTodoFiles(
    projectId: ProjectId,
    request: TodoFilesWatchRequest,
  ): Flow<TodoEvent> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val filter = resolveFilter(project, request.filter)

    val fileChangesQueue = Channel<VirtualFile>(Channel.UNLIMITED)
    launch {
      for (file in fileChangesQueue) {
        scheduleFileChanges(project, file, filter)
      }
    }

    readAction {
      blockingContextToIndicator {
        buildInitialScanEvents(project, request.scope, filter)
      }
      if (shouldUseSplitTodo()) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(
          TodoBackendPsiListener { file -> fileChangesQueue.trySend(file) },
          this@channelFlow.asDisposable()
        )
      }
    }

    awaitCancellation()
  }.buffer(Channel.UNLIMITED)

  private fun ProducerScope<TodoEvent>.buildInitialScanEvents(project: Project, scope: TodoScope, filter: TodoFilter?) {
    val psiManager = PsiManager.getInstance(project)
    trySend(TodoEvent.AllItemsRemoved)
    when (scope) {
      is TodoScope.CurrentFile -> {
        val virtualFile = scope.fileId.virtualFile()
        if (virtualFile != null && virtualFile.isValid) {
          val psiFile = psiManager.findFile(virtualFile)
          if (psiFile != null) {
            val result = buildTodoFileResult(project, psiFile, virtualFile, filter)
            if (result != null) trySend(TodoEvent.ItemUpserted(result))
          }
        }
      }
      is TodoScope.Project -> {
        PsiTodoSearchHelper.getInstance(project).processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
          val result = buildTodoFileResult(project, psiFile, virtualFile, filter)
          if (result != null) trySend(TodoEvent.ItemUpserted(result))
          true
        }
      }
      else -> {}
    }
    trySend(TodoEvent.ScanFinished)
  }

  private suspend fun ProducerScope<TodoEvent>.scheduleFileChanges(project: Project, file: VirtualFile, filter: TodoFilter? ) {
    readAction {
      val psiManager = PsiManager.getInstance(project)
      val helper = PsiTodoSearchHelper.getInstance(project)

      if (!file.isValid) {
        trySend(TodoEvent.ItemRemoved(file.rpcId()))
        return@readAction
      }

      val psiFile = psiManager.findFile(file)
      if (psiFile == null || helper.getTodoItemsCount(psiFile) == 0) {
        trySend(TodoEvent.ItemRemoved(file.rpcId()))
        return@readAction
      }
      val result = buildTodoFileResult(project, psiFile, file, filter)
      if (result != null) trySend(TodoEvent.ItemUpserted(result))
      else trySend(TodoEvent.ItemRemoved(file.rpcId()))
    }
  }

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