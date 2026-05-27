// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.nodes

import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.todo.TodoTreeBuilder
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

@ApiStatus.Internal
class TodoRemoteFileNode(
  project: Project,
  value: Value,
  private val builder: TodoTreeBuilder,
  private val singleFileMode: Boolean
) : BaseToDoNode<TodoRemoteFileNode.Value>(project, value, builder) {

  data class Value(
    val file: VirtualFile,
  )

  private fun checkValue() : Value {
    return checkNotNull(value) { "TodoRemoteFileNode value is null" }
  }

  override fun contains(element: Any?): Boolean {
    return canRepresent(element) || children.any { it.canRepresent(element) }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is TodoRemoteFileNode) return false
    return value == other.value
  }

  override fun hashCode(): Int = value?.hashCode() ?: 0

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val file = value?.file ?: return emptyList()
    val results = builder.getCachedRemoteTodos(file)

    if (results.isEmpty()) {
      return emptyList()
    }

    return Collections.unmodifiableList(
      results.map { result ->
        TodoRemoteItemNode(
          project,
          TodoRemoteItemNode.Value(file, result.navigationOffset, result.length, result.line, result.presentation),
          builder,
        )
      }
    )
  }

  override fun update(presentation: PresentationData) {
    val file = value?.file ?: return
    val fileResult = builder.getCachedRemoteTodoFile(file)

    val presentableText = if (builder.todoTreeStructure.isPackagesShown()) {
      fileResult?.name ?: file.name
    }
    else {
      if (singleFileMode) {
        fileResult?.name ?: file.name
      }
      else {
        fileResult?.presentableUrl ?: file.presentableUrl
      }
    }

    presentation.presentableText = presentableText
    presentation.setIcon(file.fileType.icon)

    val todoItemCount = fileResult?.todos?.size ?: builder.getCachedRemoteTodos(file).size
    if (todoItemCount > 0) {
      presentation.locationString = IdeBundle.message("node.todo.items", todoItemCount)
    }
  }

  override fun getFileCount(value: Value?): Int {
    return if (value != null) 1 else 0
  }

  override fun getTodoItemCount(value: Value?): Int {
    val file = value?.file ?: return 0
    return builder.getCachedRemoteTodoFile(file)?.todos?.size ?: builder.getCachedRemoteTodos(file).size
  }

  public override fun getVirtualFile(): VirtualFile {
    return checkValue().file
  }

  fun createNavigatable(project: Project): OpenFileDescriptor {
    val file = checkValue().file
    val firstTodo = builder.getCachedRemoteTodos(file).firstOrNull()
    val offset = firstTodo?.navigationOffset ?: 0
    return OpenFileDescriptor(project, file, offset)
  }

  override fun getWeight(): Int {
    return 4
  }
}
