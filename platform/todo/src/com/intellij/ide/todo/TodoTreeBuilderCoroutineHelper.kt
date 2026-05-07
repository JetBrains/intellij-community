// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.todo.rpc.TodoQuerySettings
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.todo.rpc.fileMatchesFilter
import com.intellij.ide.todo.rpc.getFilesWithTodos
import com.intellij.ide.todo.rpc.toConfig
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.tree.TreeUtil
import fleet.rpc.client.durable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

private val ASYNC_BATCH_SIZE by lazy { RegistryManager.getInstance().get("ide.tree.ui.async.batch.size") }

private val LOG = logger<TodoTreeBuilderCoroutineHelper>()

internal class TodoTreeBuilderCoroutineHelper(private val treeBuilder: TodoTreeBuilder) : Disposable {
  private val scope = CoroutineScope(SupervisorJob())
  private var remoteCacheRefreshJob: Job? = null

  init {
    Disposer.register(treeBuilder, this)
  }

  override fun dispose() {
    scope.cancel()
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  fun collectFilesFromFlow(filter: TodoFilter?, consumer: Consumer<in PsiFile>) {
    runBlockingCancellable {
      val psiManager = PsiManager.getInstance(treeBuilder.project)
      getFilesWithTodos(treeBuilder.project, filter).collect { virtualFile ->
        val psiFile = psiManager.findFile(virtualFile) ?: return@collect
        treeBuilder.cacheRemoteTodos(virtualFile, findAllTodosSuspend(treeBuilder.project, virtualFile, filter))
        consumer.accept(psiFile)
      }
    }
  }

  fun scheduleCacheAndTreeUpdate(vararg constraints: ReadConstraint): CompletableFuture<*> {
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
    scope.launch(Dispatchers.Default + ClientId.current.asContextElement()) {
      readActionBlocking {
        files.asSequence()
          .filter { it.isValid }
          .forEach { treeBuilder.markFileAsDirty(it) }

        treeBuilder.updateVisibleTree()
      }

      if (shouldUseSplitTodo()) {
        scheduleRemoteCacheRefresh(files)
      }
    }
  }

  private fun scheduleRemoteCacheRefresh(files: List<VirtualFile>) {
    remoteCacheRefreshJob?.cancel()
    remoteCacheRefreshJob = scope.launch(Dispatchers.Default + ClientId.current.asContextElement()) {
      val filter = treeBuilder.todoTreeStructure.todoFilter
      for (file in files) {
        if (!file.isValid) continue
        runCatching {
          treeBuilder.cacheRemoteTodos(file, findAllTodosSuspend(treeBuilder.project, file, filter))
        }.onFailure { e ->
          if (e is CancellationException) throw e
          LOG.warn("Failed to retrieve TODOs for ${file.path}", e)
          treeBuilder.clearRemoteTodosCache(file)
        }
      }
      readActionBlocking {
        treeBuilder.updateVisibleTree()
      }
    }
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  fun collectCurrentFileWithCachedTodos(
    psiFile: PsiFile,
    filter: TodoFilter?,
    consumer: Consumer<in PsiFile>,
  ) {
    runBlockingCancellable {
      val virtualFile = psiFile.virtualFile ?: return@runBlockingCancellable

      runCatching {
        if (!fileMatchesFilter(treeBuilder.project, virtualFile, filter)) {
          treeBuilder.clearRemoteTodosCache(virtualFile)
          return@runBlockingCancellable
        }

        val todos = findAllTodosSuspend(treeBuilder.project, virtualFile, filter)
        treeBuilder.cacheRemoteTodos(virtualFile, todos)
        consumer.accept(psiFile)
      }.onFailure { e ->
        LOG.warn("Failed to collect todos for file ${virtualFile?.path}", e)
        treeBuilder.clearRemoteTodosCache(virtualFile)
      }
    }
  }

  @ApiStatus.Internal
  suspend fun findAllTodosSuspend(
    project: Project,
    file: VirtualFile,
    filter: TodoFilter?
  ) : List<TodoResult> {
    return durable {
      val projectId = project.projectId()
      val settings = TodoQuerySettings(file.rpcId(), filter?.let { toConfig(it) })
      TodoRemoteApi.getInstance().listTodos(projectId, settings).toList()
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
