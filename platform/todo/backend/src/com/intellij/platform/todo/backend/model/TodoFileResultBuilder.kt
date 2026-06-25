// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.todo.backend.model

import com.intellij.ide.todo.TodoFilter
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.search.PsiTodoSearchHelper
import com.intellij.psi.search.TodoAttributesUtil
import com.intellij.psi.search.TodoItem
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal object TodoFileResultBuilder {

  fun buildTodoFileResult(
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

  fun collectTodoResults(
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