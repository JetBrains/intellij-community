// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.ide.todo.rpc.TodoEvent
import com.intellij.ide.todo.rpc.TodoFileResult
import com.intellij.ide.vfs.VirtualFileId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class TodoModelState(
  @JvmField val files: Map<VirtualFileId, TodoFileResult> = emptyMap(),
  @JvmField val scanFinished: Boolean = false,
)

internal class FrontendTodoModel {
  private val state = MutableStateFlow(TodoModelState())

  fun applyEvent(event: TodoEvent) {
    state.update { old ->
      val files = LinkedHashMap(old.files)
      var scanFinished = old.scanFinished
      when (event) {
        is TodoEvent.ItemUpserted -> files[event.item.fileId] = event.item
        is TodoEvent.ItemRemoved -> files.remove(event.fileId)
        TodoEvent.AllItemsRemoved -> {
          files.clear()
          scanFinished = false
        }
        TodoEvent.ScanFinished -> scanFinished = true
      }
      TodoModelState(files, scanFinished)
    }
  }
}