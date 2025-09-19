// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.rpc

import com.intellij.ide.vfs.VirtualFileId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Serializable
data class TodoPatternConfig(
  val pattern: String,
  val isCaseSensitive: Boolean,
)

@ApiStatus.Internal
@Serializable
data class TodoFilterConfig(
  val name: String? = null,
  val patterns: List<TodoPatternConfig> = emptyList(),
)

@ApiStatus.Internal
@Serializable
data class TodoQuerySettings(
  val fileId: VirtualFileId,
  val filter: TodoFilterConfig? = null,
  val maxItems: Int = 10_000,
)