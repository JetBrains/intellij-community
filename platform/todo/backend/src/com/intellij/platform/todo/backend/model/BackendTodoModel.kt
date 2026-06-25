// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.model

import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.shouldUseSplitTodo
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.todo.backend.model.TodoFileResultBuilder.buildTodoFileResult
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.util.asDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

internal class BackendTodoModel(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
  private val scope: TodoScope,
  private val filter: TodoFilter?
) {
  private val _events: Channel<TodoEvent> = Channel(Channel.UNLIMITED)
  val events: Flow<TodoEvent> = _events.receiveAsFlow()
  private val initialScanFinished = CompletableDeferred<Unit>()

  init {
    coroutineScope.launch {
      // while project scan works in project scope, because of the read action, the scan will be restarted
      // TODO keep track of counter at least
      val initalEvents = readAction {
        val events: List<TodoEvent> = blockingContextToIndicator {
          buildInitialScanEvents()
        }
        if (shouldUseSplitTodo()) {
          PsiManager.getInstance(project).addPsiTreeChangeListener(
            TodoBackendPsiListener(::scheduleFileChanges),
            coroutineScope.asDisposable()
          )
        }
        events
      }

      try {
        for (event in initalEvents) {
          _events.send(event)
        }
      }
      finally {
        initialScanFinished.complete(Unit)
      }
    }
  }

  fun scheduleFileChanges(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return
    coroutineScope.launch {
      initialScanFinished.await()
      applyFileChanges(files)
    }
  }

  private fun buildInitialScanEvents() : List<TodoEvent> {
    val psiManager = PsiManager.getInstance(project)
    val events = mutableListOf<TodoEvent>()
    events.add(TodoEvent.AllItemsRemoved)
    when (scope) {
      is TodoScope.CurrentFile -> {
        val virtualFile = scope.fileId.virtualFile()
        if (virtualFile != null && virtualFile.isValid) {
          val psiFile = psiManager.findFile(virtualFile)
          if (psiFile != null) {
            val result = buildTodoFileResult(project, psiFile, virtualFile, filter)
            if (result != null) events.add(TodoEvent.ItemUpserted(result))
          }
        }
      }
      is TodoScope.Project -> {
        PsiTodoSearchHelper.getInstance(project).processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
          val result = buildTodoFileResult(project, psiFile, virtualFile, filter)
          if (result != null) events.add(TodoEvent.ItemUpserted(result))
          true
        }
      }
      else -> {}
    }
    events.add(TodoEvent.ScanFinished)
    return events
  }

  private suspend fun applyFileChanges(files: Collection<VirtualFile>) {
    if (files.isEmpty()) return

    readAction {
      val psiManager = PsiManager.getInstance(project)
      val helper = PsiTodoSearchHelper.getInstance(project)
      for (file in files) {
        if (!file.isValid) {
          _events.trySend(TodoEvent.ItemRemoved(file.rpcId()))
          continue
        }
        val psiFile = psiManager.findFile(file)
        if (psiFile == null || helper.getTodoItemsCount(psiFile) == 0) {
          _events.trySend(TodoEvent.ItemRemoved(file.rpcId()))
          continue
        }
        val result = buildTodoFileResult(project, psiFile, file, filter)
        if (result != null) _events.trySend(TodoEvent.ItemUpserted(result))
        else _events.trySend(TodoEvent.ItemRemoved(file.rpcId()))
      }
    }
  }
}