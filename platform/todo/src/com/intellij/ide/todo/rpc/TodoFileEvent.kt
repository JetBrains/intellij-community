// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable


@ApiStatus.Internal
@Serializable
enum class TodoFileEventType {
  Updated,
  Removed,
  InitialScanFinished,
  Reset
}

@ApiStatus.Internal
@Serializable
data class TodoFileEvent(
  val type: TodoFileEventType,
  val fileId: VirtualFileId? = null,
  val file: TodoFileResult? = null
)


