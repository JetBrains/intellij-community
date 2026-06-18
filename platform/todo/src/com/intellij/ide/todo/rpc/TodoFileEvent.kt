// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable

@ApiStatus.Internal
@Serializable
sealed interface TodoFileEvent {
  @Serializable
  data class Changes(
    val updated: List<TodoFileResult> = emptyList(),
    val removed: List<VirtualFileId> = emptyList(),
    val initialScanFinished: Boolean = false
  ) : TodoFileEvent

  @Serializable
  data object Reset : TodoFileEvent
}
