// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.todo.model.FrontendTodoModel
import com.intellij.ide.todo.model.TodoModelChange
import com.intellij.ide.todo.model.TodoScope
import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.collectWatchedTodoFiles
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import java.util.concurrent.CompletableFuture

private val ASYNC_BATCH_SIZE by lazy { RegistryManager.getInstance().get("ide.tree.ui.async.batch.size") }

private val LOG = logger<TodoTreeBuilderCoroutineHelper>()

internal class TodoTreeBuilderCoroutineHelper(private val treeBuilder: TodoTreeBuilder) : Disposable {
  private val parentScope = treeBuilder.project.service<TodoCoroutineScopeProvider>().coroutineScope
  private val scope = parentScope.childScope("TodoTreeBuilderCoroutineHelper")
  private var remoteTodoFilesWatchJob: Job? = null
  private val _model = FrontendTodoModel()
  val model: FrontendTodoModel get() = _model

  init {
    Disposer.register(treeBuilder, this)
  }

  override fun dispose() {
    remoteTodoFilesWatchJob?.cancel()
    scope.cancel()
  }

  fun scheduleRemoteTodoFilesWatch(vararg constraints: ReadConstraint): CompletableFuture<*> {
    LOG.debug("TODO watch (frontend): scheduleRemoteTodoFilesWatch start, builder=${treeBuilder.javaClass.name}")
    remoteTodoFilesWatchJob?.cancel()

    val todoScope: TodoScope = treeBuilder.scope ?: return CompletableFuture.completedFuture(null)
    val filter = treeBuilder.todoTreeStructure.todoFilter
    val scanCompleted = CompletableFuture<Unit>()

    remoteTodoFilesWatchJob = this.scope.launch(Dispatchers.Default + ClientId.current.asContextElement()) {
      readAction { treeBuilder.clearCache() }
      collectWatchedTodoFiles(treeBuilder.project, todoScope, filter) { event ->
        coroutineContext.ensureActive()
        val change = _model.applyEvent(event)
        when (change) {
          is TodoModelChange.FileUpdated -> treeBuilder.addRemoteTodoFileToTree(change.file)
          is TodoModelChange.FileRemoved -> treeBuilder.removeRemoteTodoFileFromTree(change.file)
          TodoModelChange.Cleared -> treeBuilder.clearCache()
          TodoModelChange.Nothing -> {}
          }
        if (event is TodoEvent.ScanFinished) {
          scanCompleted.complete(Unit)
        }
        readAction { treeBuilder.updateVisibleTree() }
      }
    }
    return scanCompleted
  }

  fun scheduleCacheAndTreeUpdate(vararg constraints: ReadConstraint): CompletableFuture<*> {
    val todoScope = treeBuilder.scope
    if (shouldUseSplitTodo() && todoScope != null) {
      return scheduleRemoteTodoFilesWatch(*constraints)
    }
    return scope.launch(Dispatchers.EDT + ClientId.current.asContextElement()) {
      treeBuilder.onUpdateStarted()
      constrainedReadAction(*constraints) {
        blockingContextToIndicator {
          treeBuilder.collectFiles()
        }
      }
      treeBuilder.onUpdateFinished()
    }.asCompletableFuture()
  }

  fun scheduleCacheValidationAndTreeUpdate() {
    scope.launch(Dispatchers.EDT + ClientId.current.asContextElement()) {
      val pathsToSelect = TreeUtil.collectSelectedUserObjects(treeBuilder.tree).stream()
      treeBuilder.tree.clearSelection()

      readAction {
        treeBuilder.validateCacheAndUpdateTree()
      }

      TreeUtil.promiseSelect(
        treeBuilder.tree,
        pathsToSelect.map { TodoTreeBuilder.getVisitorFor(it) },
      )
    }
  }

  fun scheduleUpdateTree(): CompletableFuture<*> {
    return scope.launch(Dispatchers.Default + ClientId.current.asContextElement()) {
      readActionBlocking {
        treeBuilder.updateVisibleTree()
      }
    }.asCompletableFuture()
  }

  fun scheduleMarkFilesAsDirtyAndUpdateTree(files: List<VirtualFile>) {
    if (shouldUseSplitTodo() && remoteTodoFilesWatchJob?.isActive == true) {
      return
    }

    scope.launch(Dispatchers.Default + ClientId.current.asContextElement()) {
      readActionBlocking {
        files.asSequence()
          .filter { it.isValid }
          .forEach { treeBuilder.markFileAsDirty(it) }

        treeBuilder.updateVisibleTree()
      }
    }
  }
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.collectFiles() {
  ProgressManager.checkCanceled()
  clearCache()

  collectFiles {
    myFileTree.add(it.virtualFile)

    if (myFileTree.size() % ASYNC_BATCH_SIZE.asInteger() == 0) {
      validateCacheAndUpdateTree()
    }
  }

  validateCacheAndUpdateTree()
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.validateCacheAndUpdateTree() {
  ProgressManager.checkCanceled()

  todoTreeStructure.validateCache()
  updateVisibleTree()
}

@RequiresBackgroundThread
@RequiresReadLock
private fun TodoTreeBuilder.updateVisibleTree() {
  if (isUpdatable) {
    if (hasDirtyFiles()) { // suppress redundant cache validations
      todoTreeStructure.validateCache()
    }
    model.invalidateAsync()
  }
}
