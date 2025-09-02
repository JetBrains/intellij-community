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
import java.util.concurrent.atomic.AtomicInteger
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
import kotlinx.coroutines.flow.buffer
import java.util.regex.Pattern

private val LOG: Logger = logger<TodoRemoteApiImpl>()

internal class TodoRemoteApiImpl : TodoRemoteApi {
  override suspend fun listTodos(projectId: ProjectId, settings: TodoQuerySettings): Flow<TodoResult> {
    val sentItems = AtomicInteger(0)

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

      readAction {
        val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (psiFile == null) {
          LOG.warn("PsiFile not found for virtualFile path ${virtualFile.path}")
          return@readAction
        }

        val document = psiFile.viewProvider.document
        val helper = PsiTodoSearchHelper.getInstance(project)
        val filter = resolveFilter(project, settings.filter)

        val allTodoItems = helper.findTodoItems(psiFile)
        val filteredTodoItems = if (filter != null) allTodoItems.filter { it.pattern != null && filter.contains(it.pattern) } else allTodoItems.asList()
        if (filteredTodoItems.isEmpty()) return@readAction

        for (todoItem in filteredTodoItems) {
          if (sentItems.get() >= settings.maxItems) break

          val start = todoItem.textRange.startOffset
          val end = todoItem.textRange.endOffset
          val line = if (document != null) document.getLineNumber(start) else 0

          val previewChunks = buildPreviewChunks(document, line)

          val result = TodoResult(
            presentation = previewChunks,
            fileId = virtualFile.rpcId(),
            line = line,
            navigationOffset = start,
            length = end - start,
          )

          val sent = trySend(result)
          if (sent.isSuccess) sentItems.incrementAndGet()
        }
      }
    }.buffer(capacity = settings.maxItems)
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
        val patternString = if (config.isRegex) config.pattern else Pattern.quote(config.pattern)
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