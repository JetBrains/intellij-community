// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.todo.rpc.TodoResult
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

internal class TodoModel {
  private val files = ConcurrentHashMap<VirtualFileId, TodoFileResult>()

  fun getFileResult(file: VirtualFile): TodoFileResult? = files[file.rpcId()]
  fun getTodosForFile(file: VirtualFile): List<TodoResult> = files[file.rpcId()]?.todos ?: emptyList()
  fun hasTodos(file: VirtualFile): Boolean = files.containsKey(file.rpcId())

  fun applyEvent(event: TodoEvent) : TodoModelChange {
    when (event) {
      is TodoEvent.ItemUpserted -> {
        val file = event.item.fileId.virtualFile() ?: return TodoModelChange.Nothing
        if (!file.isValid || event.item.todos.isEmpty()) return removeFile(file)
        files[file.rpcId()] = event.item
        return TodoModelChange.FileUpdated(file)
      }
      is TodoEvent.ItemRemoved -> {
        val file = event.fileId.virtualFile() ?: return TodoModelChange.Nothing
        return removeFile(file)
      }
      TodoEvent.AllItemsRemoved -> {
        files.clear()
        return TodoModelChange.Cleared
      }
      TodoEvent.ScanFinished -> {
        return TodoModelChange.Nothing
      }
    }
  }

  private fun removeFile(file: VirtualFile): TodoModelChange {
    val removed = files.remove(file.rpcId())
    return if (removed != null) TodoModelChange.FileRemoved(file) else TodoModelChange.Nothing
  }
}