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
import com.intellij.psi.PsiTreeChangeListener
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoItem
import com.intellij.psi.search.TodoPattern
import com.intellij.util.text.CharArrayUtil
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch

private val LOG: Logger = logger<TodoRemoteApiImpl>()

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
      val initialResults : List<TodoFileResult> = readAction {
        blockingContextToIndicator {
          if (watchedFile != null) {
            val psiFile = PsiManager.getInstance(project).findFile(watchedFile) ?: return@blockingContextToIndicator emptyList()
            val result = buildTodoFileResult(project, psiFile, watchedFile, resolvedFilter)

            if (result != null) {
              listOf(result)
            }
            else {
              emptyList()
            }
          }
          else {
            val helper = PsiTodoSearchHelper.getInstance(project)
            val fileResults = mutableListOf<TodoFileResult>()

            helper.processFilesWithTodoItems { psiFile ->
              val virtualFile = psiFile.virtualFile
              if (virtualFile == null) {
                System.out.println("TODO watch backend: psiFile without virtualFile=${psiFile.name}")
                return@processFilesWithTodoItems true
              }
              val result = buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter)
              if (result != null) {
                fileResults.add(result)
              }
              true
            }
            fileResults
          }
        }
      }

      for (result in initialResults) {
        cache[result.fileId] = result
        System.out.println("TODO watch backend: sending Updated fileId=${result.fileId}, todos=${result.todos.size}")
        send(TodoFileEvent.Updated(result))
      }
      send(TodoFileEvent.InitialScanFinished)
    }

    suspend fun updateFile(virtualFile: VirtualFile) {
      if (!virtualFile.isValid) {
        val fileId = virtualFile.rpcId()
        if (cache.remove(fileId) != null) {
          send(TodoFileEvent.Removed(fileId))
        }
        return
      }
      if (watchedFile != null && watchedFile != virtualFile) {
        return
      }

      val result = readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction null
        buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter)
      }

      val fileId = virtualFile.rpcId()
      if (result == null) {
        if (cache.remove(fileId) != null) {
          send(TodoFileEvent.Removed(fileId))
        }
      }
      else {
        cache[fileId] = result
        send(TodoFileEvent.Updated(result))
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
      dirtyFiles.consumeAsFlow().collect { file ->
        updateFile(file)
      }
    }

    awaitClose {
      updateJob.cancel()
      dirtyFiles.close()
      Disposer.dispose(listenerDisposable)
    }
  }

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

    val results = readAction {
      blockingContextToIndicator {
        val helper = PsiTodoSearchHelper.getInstance(project)
        val fileResults = mutableListOf<TodoFileResult>()

        helper.processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true
          val result = buildTodoFileResult(project, psiFile, virtualFile, resolvedFilter)
          if (result != null) {
            fileResults.add(result)
          }
          true
        }
        fileResults
      }
    }
    for (result in results) {
      send(result)
    }
  }

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

    val results = readAction {
      blockingContextToIndicator {
        val helper = PsiTodoSearchHelper.getInstance(project)
        val fileIds = mutableListOf<VirtualFileId>()

        helper.processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true

          val matchesFilter = if (resolvedFilter != null) {
            resolvedFilter.accept(helper, psiFile)
          } else {
            helper.getTodoItemsCount(psiFile) > 0
          }

          if (matchesFilter) {
            fileIds.add(virtualFile.rpcId())
          }
          true
        }
        fileIds
      }
    }

    for (result in results) {
      send(result)
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