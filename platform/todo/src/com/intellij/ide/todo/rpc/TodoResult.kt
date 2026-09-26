// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.ui.SerializableTextChunk
import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TodoResult(
  val presentation: List<SerializableTextChunk>,
  val fileId: VirtualFileId,
  val line: Int,
  val navigationOffset: Int,
  val length: Int,
)