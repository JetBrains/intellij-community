// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.ide.vfs.VirtualFileId
import org.jetbrains.annotations.ApiStatus
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.NonNls

@ApiStatus.Internal
@Serializable
sealed interface TodoScope {
  @Serializable
  data object Project : TodoScope

  @Serializable
  data class CurrentFile(val fileId: VirtualFileId) : TodoScope

  @Serializable
  data class NamedScope(val scopeId: @NonNls String) : TodoScope
}