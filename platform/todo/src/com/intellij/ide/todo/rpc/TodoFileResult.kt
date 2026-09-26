// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TodoFileResult(
  val fileId: VirtualFileId,
  val name: String,
  val presentableUrl: String,
  val moduleName: String?,
  val packageName: String?,
  val todos: List<TodoResult>,
)
