// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable

@ApiStatus.Internal
@Serializable
sealed interface TodoEvent {
  @Serializable
  data class ItemUpserted(val item: TodoFileResult) : TodoEvent

  @Serializable
  data class ItemRemoved(val fileId: VirtualFileId) : TodoEvent

  @Serializable
  data object AllItemsRemoved : TodoEvent

  @Serializable
  data object ScanFinished : TodoEvent
}
