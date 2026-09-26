// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.todo.model

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull

internal sealed interface TodoModelChange {
  data class FileUpdated(@NotNull val file: VirtualFile) : TodoModelChange
  data class FileRemoved(@NotNull val file: VirtualFile) : TodoModelChange
  data object Cleared : TodoModelChange
  data object Nothing : TodoModelChange
}