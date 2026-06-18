// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.rpc.TodoFileEvent
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.todo.rpc.TodoFilesWatchRequest
import com.intellij.ide.todo.rpc.TodoFilterConfig
import com.intellij.ide.todo.rpc.TodoPatternConfig
import com.intellij.ide.todo.rpc.TodoQuerySettings
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoItem
import com.intellij.psi.search.TodoPattern
import com.intellij.util.text.CharArrayUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private val LOG: Logger = logger<TodoRemoteApiImpl>()

private const val TODO_WATCH_BATCH_SIZE = 50
private const val TODO_WATCH_INCREMENTAL_BATCH_SIZE = 20
private const val TODO_WATCH_INCREMENTAL_BATCH_DELAY_MS = 100L

internal class TodoRemoteApiImpl : TodoRemoteApi {
  override fun watchTodoFiles(
    projectId: ProjectId,
    request: TodoFilesWatchRequest,
  ): Flow<TodoFileEvent> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val resolvedFilter = resolveFilter(project, request.filter)
    val watchedFile = request.fileId?.virtualFile()

    val cache = linkedMapOf<VirtualFileId, TodoFileResult>()
    val dirtyFiles = Channel<VirtualFile>(Channel.UNLIMITED)

    suspend fun collectInitialSnapshot() {
      val updated = mutableListOf<TodoFileResult>()
      val removed = mutableListOf<VirtualFileId>()

      fun flushInitialBatchIfNeeded() {
        if (updated.size + removed.size < TODO_WATCH_BATCH_SIZE) {
          return
        }

        trySend(
          TodoFileEvent.Changes(
            updated = updated.toList(),
            removed = removed.toList(),
          )
        )

        updated.clear()
        removed.clear()
      }

      readAction {
        blockingContextToIndicator {
          System.out.println("TODO watch backend: started watchedFile=${watchedFile?.path}")

          if (watchedFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(watchedFile) ?: return@blockingContextToIndicator
            val result = buildTodoFileResult(project, psiFile, watchedFile, resolvedFilter)

            if (result != null) {
              cache[result.fileId] = result
              updated.add(result)
            }
          }
          else {
            val helper = PsiTodoSearchHelper.getInstance(project)

            helper.processFilesWithTodoItems { psiFile ->
              val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
              val result = buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter) ?: return@processFilesWithTodoItems true

              cache[result.fileId] = result
              updated.add(result)
              flushInitialBatchIfNeeded()

              true
            }
          }
        }
      }

      send(
        TodoFileEvent.Changes(
          updated = updated.toList(),
          removed = removed.toList(),
          initialScanFinished = true,
        )
      )
      System.out.println(
        "TODO watch backend: initial batch sent watchedFile=${watchedFile?.path}, updated=${updated.size}, initialScanFinished=true"
      )
    }

    suspend fun updateFiles(files: Collection<VirtualFile>) {
      val updated = mutableListOf<TodoFileResult>()
      val removed = mutableListOf<VirtualFileId>()

      for (virtualFile in files) {
        if (!virtualFile.isValid) {
          val fileId = virtualFile.rpcId()
          if (cache.remove(fileId) != null) {
            removed.add(fileId)
          }
          continue
        }
        if (watchedFile != null && watchedFile != virtualFile) {
          continue
        }

        val result = readAction {
          val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction null
          buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter)
        }

        val fileId = virtualFile.rpcId()
        if (result == null) {
          if (cache.remove(fileId) != null) {
            removed.add(fileId)
          }
        }
        else {
          cache[fileId] = result
          updated.add(result)
        }
      }

      if (updated.isNotEmpty() || removed.isNotEmpty()) {
        send(
          TodoFileEvent.Changes(
            updated = updated,
            removed = removed,
          )
        )
      }
    }

    collectInitialSnapshot()
    val listenerDisposable = Disposer.newDisposable("TODO file watcher")
    PsiManager.getInstance(project).addPsiTreeChangeListenerBackgroundable(object : PsiTreeChangeAdapter() {
      override fun childAdded(event: PsiTreeChangeEvent) {
        collectDirtyFile(event)?.let { dirtyFiles.trySend(it) }
      }

      override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
        collectDirtyFile(event)?.let { dirtyFiles.trySend(it) }
      }

      override fun childReplaced(event: PsiTreeChangeEvent) {
        collectDirtyFile(event)?.let { dirtyFiles.trySend(it) }
      }

      override fun childrenChanged(event: PsiTreeChangeEvent) {
        collectDirtyFile(event)?.let { dirtyFiles.trySend(it) }
      }

      override fun propertyChanged(event: PsiTreeChangeEvent) {
        collectDirtyFile(event)?.let { dirtyFiles.trySend(it) }
      }
    }, listenerDisposable)

    val updateJob = launch {
      val pendingFiles = linkedSetOf<VirtualFile>()

      while (isActive) {
        pendingFiles.add(dirtyFiles.receive())

        withTimeoutOrNull(TODO_WATCH_INCREMENTAL_BATCH_DELAY_MS) {
          while (pendingFiles.size < TODO_WATCH_INCREMENTAL_BATCH_SIZE) {
            pendingFiles.add(dirtyFiles.receive())
          }
        }

        val filesToUpdate = pendingFiles.toList()
        pendingFiles.clear()

        updateFiles(filesToUpdate)
      }
    }

    awaitClose {
      updateJob.cancel()
      dirtyFiles.close()
      Disposer.dispose(listenerDisposable)
    }
  }.buffer(Channel.UNLIMITED)

  private fun collectDirtyFile(event: PsiTreeChangeEvent): VirtualFile? {
    val eventFile = event.file?.virtualFile
    if (eventFile != null) {
      return eventFile
    }
    return null
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

  private fun collectTodoResults(
    project: Project,
    psiFile: PsiFile,
    virtualFile: VirtualFile,
    filter: TodoFilter?,
  ) : List<TodoResult> {
    val document = psiFile.viewProvider?.document

    val allTodoItems = PsiTodoSearchHelper.getInstance(project).findTodoItems(psiFile)
    val filteredTodoItems = if (filter != null) {
      allTodoItems.filter { it.pattern != null && filter.contains(it.pattern) }
    } else allTodoItems.asList()

    return filteredTodoItems
      .sortedWith(compareBy({ it.textRange.startOffset }, {it.textRange.endOffset}))
      .map { todoItem ->
        val (line, preview) = if (document != null) {
          val startOffset = todoItem.textRange.startOffset
          val line = document.getLineNumber(startOffset)
          val previewChunks = buildPreviewChunks(document, todoItem, line)
          line to previewChunks
        } else 0 to emptyList()

        TodoResult(
          presentation = preview,
          fileId = virtualFile.rpcId(),
          line = line,
          navigationOffset = todoItem.textRange.startOffset,
          length = todoItem.textRange.endOffset - todoItem.textRange.startOffset
        )
      }
  }

  private fun buildTodoFileResult(
    project: Project,
    psiFile: PsiFile,
    virtualFile: VirtualFile,
    filter: TodoFilter?
  ) : TodoFileResult? {
    val helper = PsiTodoSearchHelper.getInstance(project)

    val matchesFilter = if (filter != null) {
      filter.accept(helper, psiFile)
    }
    else {
      helper.getTodoItemsCount(psiFile) > 0
    }
    if (!matchesFilter) {
      return null
    }

    val todos = collectTodoResults(project, psiFile, virtualFile, filter)
    if (todos.isEmpty()) {
      return null
    }

    return TodoFileResult(
      fileId = virtualFile.rpcId(),
      name = virtualFile.name,
      presentableUrl = virtualFile.presentableUrl,
      moduleName = getModuleName(project, virtualFile),
      packageName = getPackageName(project, virtualFile),
      todos = todos,
    )
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

  private fun buildPreviewChunks(document: Document?, todoItem : TodoItem, line: Int) : List<SerializableTextChunk> {
    if (document == null || document.lineCount == 0) return emptyList()

    val chars = document.charsSequence

    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val lineStartNonWs = CharArrayUtil.shiftForward(chars, lineStart, " \t")

    val text = chars.subSequence(lineStartNonWs, lineEnd).toString()

    val startInLine = todoItem.textRange.startOffset - lineStartNonWs
    val endInLine = todoItem.textRange.endOffset - lineStartNonWs
    if (startInLine < 0 || endInLine <= startInLine || endInLine > text.length) {
      return listOf(SerializableTextChunk(text))
    }

    val attrs = todoItem.pattern?.attributes?.textAttributes ?: TodoAttributesUtil.getDefaultColorSchemeTextAttributes()

    return buildList {
      if (startInLine > 0) {
        add(SerializableTextChunk(text.substring(0, startInLine)))
      }
      add(SerializableTextChunk(text.substring(startInLine, endInLine), attrs))
      if (endInLine < text.length) {
        add(SerializableTextChunk(text.substring(endInLine)))
      }
    }
  }

  private fun getModuleName(project: Project, virtualFile: VirtualFile): String? {
    return ModuleUtilCore.findModuleForFile(virtualFile, project)?.name
  }

  private fun getPackageName(project: Project, virtualFile: VirtualFile): String? {
    val fileIndex = ProjectRootManager.getInstance(project).fileIndex
    val sourceRoot = fileIndex.getSourceRootForFile(virtualFile) ?: return null
    val parent = virtualFile.parent ?: return null

    val relativePath = VfsUtilCore.getRelativePath(parent, sourceRoot, '/') ?: return null
    return relativePath?.takeIf { it.isNotEmpty() }
  }
}