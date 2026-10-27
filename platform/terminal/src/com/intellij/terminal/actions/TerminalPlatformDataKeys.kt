// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal.actions

import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TerminalPlatformDataKeys {
  @JvmField
  val EDITOR_KEY: DataKey<Editor> = DataKey.create("TERMINAL_EDITOR")
}
