// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
sealed interface TodoEvent {
  @Serializable
  data class FileUpserted(val item: TodoFileResult) : TodoEvent

  @Serializable
  data class FileRemoved(val fileId: VirtualFileId) : TodoEvent

  @Serializable
  data object AllItemsRemoved : TodoEvent

  @Serializable
  data object ScanFinished : TodoEvent
}
