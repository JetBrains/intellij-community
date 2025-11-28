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
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoPattern
import java.util.regex.Pattern

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {
  override suspend fun listTodos(projectId: ProjectId, settings: TodoQuerySettings): Flow<TodoResult> {
    return channelFlow {
      val project = projectId.findProjectOrNull()
      if (project == null) {
        LOG.warn("Project not found for projectId ${projectId}")
        return@channelFlow
      }

      val virtualFile = settings.fileId.virtualFile()
      if (virtualFile == null) {
        LOG.warn("VirtualFile not found for fileId ${settings.fileId}")
        return@channelFlow
      }

      val filter = resolveFilter(project, settings.filter)

      // lightweight descriptor of a TODO occurrence containing only offsets
      // introduced in order to separate filtering from preview computation to keep read locks as short as possible
      data class TodoOccurence(val start: Int, val end: Int)

      val todoItems: List<TodoOccurence> = readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile == null) {
          LOG.warn("PsiFile not found for virtualFile path ${virtualFile.path}")
          return@readAction emptyList()
        }

        val helper = PsiTodoSearchHelper.getInstance(project)
        val allTodoItems = helper.findTodoItems(psiFile)

        val filteredTodoItems = if (filter != null) {
          allTodoItems.filter { it.pattern != null && filter.contains(it.pattern) }
        } else allTodoItems.asList()

        filteredTodoItems.map { todoItem ->
          TodoOccurence(
            start = todoItem.textRange.startOffset,
            end = todoItem.textRange.endOffset,
          )
        }
      }

      val document: Document? = readAction {
        PsiManager.getInstance(project).findFile(virtualFile)?.viewProvider?.document
      }

      for (item in todoItems) {
        val (line, preview) = readAction {
          if (document != null) {
            val line = document.getLineNumber(item.start)
            val previewChunks = buildPreviewChunks(document, line)
            line to previewChunks
          } else 0 to emptyList()
        }

        val result = TodoResult(
          presentation = preview,
          fileId = virtualFile.rpcId(),
          line = line,
          navigationOffset = item.start,
          length = item.end - item.start
        )

        trySend(result)
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