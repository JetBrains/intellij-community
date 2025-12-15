// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.rpc

import com.intellij.ide.todo.TodoConfiguration
import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.rpc.TodoFilterConfig
import com.intellij.ide.todo.rpc.TodoPatternConfig
import com.intellij.ide.todo.rpc.TodoQuerySettings
import com.intellij.ide.todo.rpc.TodoRemoteApi
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.blockingContextToIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {
  override suspend fun listTodos(
    projectId: ProjectId,
    settings: TodoQuerySettings
  ): Flow<TodoResult> = channelFlow {
    val project = projectId.findProjectOrNull() ?: return@channelFlow
    val virtualFile = settings.fileId.virtualFile() ?: return@channelFlow
    val filter = resolveFilter(project, settings.filter)

    val results: List<TodoResult> = readAction {
      val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return@readAction emptyList()
      val document = psiFile.viewProvider?.document

      val allTodoItems = PsiTodoSearchHelper.getInstance(project).findTodoItems(psiFile)
      val filteredTodoItems = if (filter != null) {
        allTodoItems.filter { it.pattern != null && filter.contains(it.pattern) }
      } else allTodoItems.asList()

      filteredTodoItems.map { todoItem ->
        val (line, preview) = if (document != null) {
          val startOffset = todoItem.textRange.startOffset
          val line = document.getLineNumber(startOffset)
          val previewChunks = buildPreviewChunks(document, line)
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

    for (result in results) {
      trySend(result)
    }
  }

  override suspend fun getFilesWithTodos(
    projectId: ProjectId,
    filter: TodoFilterConfig?,
  ): List<VirtualFileId> {

    val project = projectId.findProjectOrNull() ?: return emptyList()
    val resolvedFilter = resolveFilter(project, filter)

    return readAction {
      blockingContextToIndicator {
        val files = mutableListOf<VirtualFileId>()
        val helper = PsiTodoSearchHelper.getInstance(project)

        helper.processFilesWithTodoItems { psiFile ->
          val virtualFile = psiFile.virtualFile ?: return@processFilesWithTodoItems true

          val matchesFilter = if (resolvedFilter != null) {
            resolvedFilter.accept(helper, psiFile)
          } else {
            helper.getTodoItemsCount(psiFile) > 0
          }

          if (matchesFilter) {
            files.add(virtualFile.rpcId())
          }
          true
        }
        files
      }
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

  private fun buildPreviewChunks(document: Document?, line: Int) : List<SerializableTextChunk> {
    if (document == null || document.lineCount == 0) return emptyList()
    val lineStart = document.getLineStartOffset(line)
    val lineEnd = document.getLineEndOffset(line)
    val text = document.charsSequence.subSequence(lineStart, lineEnd).toString()
    return listOf(SerializableTextChunk(text))
  }
}