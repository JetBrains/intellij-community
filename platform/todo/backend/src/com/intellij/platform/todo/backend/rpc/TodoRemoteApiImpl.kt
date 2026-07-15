// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.TodoFilesWatchRequest
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.model.toSearchScope
import com.intellij.ide.todo.rpc.toTodoFilter
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.todo.backend.model.TodoBackendPsiListener
import com.intellij.platform.todo.backend.model.TodoFileResultBuilder.buildTodoFileResult
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.SearchScope
import com.intellij.util.asDisposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.beans.PropertyChangeListener

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {

  override fun watchTodoFiles(
    projectId: ProjectId,
    request: TodoFilesWatchRequest,
  ): Flow<TodoEvent> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val filter = request.filter?.toTodoFilter()
    val searchScope = request.scope.toSearchScope(project)

    val flowDisposable = this@channelFlow.asDisposable()
    var scanDisposable: Disposable? = null

    val fileChangesQueue = Channel<VirtualFile>(Channel.UNLIMITED)
    var initialScanJob: Job? = null

    fun scheduleInitialScan() {
      initialScanJob?.cancel()
      scanDisposable?.let(Disposer::dispose)

      val currentScanDisposable = Disposer.newDisposable(flowDisposable);
      scanDisposable = currentScanDisposable

      initialScanJob = launch {
        readAction {
          blockingContextToIndicator {
            buildInitialScanEvents(project, request.scope, searchScope, filter)
          }
          PsiManager.getInstance(project).addPsiTreeChangeListener(
            TodoBackendPsiListener { file -> if (searchScope?.contains(file) != false) fileChangesQueue.trySend(file) },
            currentScanDisposable
          )
        }
      }
    }

    launch {
      for (file in fileChangesQueue) {
        scheduleFileChanges(project, file, filter)
      }
    }

    project.messageBus.connect(flowDisposable).subscribe(
      TodoConfiguration.PROPERTY_CHANGE,
      PropertyChangeListener { event ->
        if (event.propertyName == TodoConfiguration.PROP_TODO_PATTERNS ||
            event.propertyName == TodoConfiguration.PROP_TODO_FILTERS ||
            event.propertyName == TodoConfiguration.PROP_MULTILINE) {
          scheduleInitialScan()
        }
      }
    )

    scheduleInitialScan()

    awaitCancellation()
  }.buffer(Channel.UNLIMITED)

  private fun ProducerScope<TodoEvent>.buildInitialScanEvents(project: Project, scope: TodoScope, searchScope: SearchScope?, filter: TodoFilter?) {
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
      is TodoScope.NamedScope -> {
        if (searchScope == null) {
          LOG.error("Search scope is null")
          return
        }
        PsiTodoSearchHelper.getInstance(project).processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
          if (!searchScope.contains(virtualFile)) return@processFilesWithTodoItems true
          val result = buildTodoFileResult(project, psiFile, virtualFile, filter)
          if (result != null) trySend(TodoEvent.ItemUpserted(result))
          true
        }
      }
    }
    trySend(TodoEvent.ScanFinished)
  }

  private suspend fun ProducerScope<TodoEvent>.scheduleFileChanges(project: Project, file: VirtualFile, filter: TodoFilter?) {
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
}