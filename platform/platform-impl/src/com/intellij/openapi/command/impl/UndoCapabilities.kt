// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import org.jetbrains.annotations.ApiStatus.Internal


@Internal
interface UndoCapabilities {
  fun isTransparentSupported(): Boolean
  fun isConfirmationSupported(): Boolean
  fun isCompactSupported(): Boolean
  fun isGlobalSplitSupported(): Boolean
  fun isPerClientSupported(): Boolean
  fun isCommandRestartSupported(): Boolean
  fun isEditorStateRestoreSupported(): Boolean

  object Default : UndoCapabilities {
    override fun isTransparentSupported(): Boolean = true
    override fun isConfirmationSupported(): Boolean = true
    override fun isCompactSupported(): Boolean = true
    override fun isGlobalSplitSupported(): Boolean = true
    override fun isPerClientSupported(): Boolean = true
    override fun isCommandRestartSupported(): Boolean = true
    override fun isEditorStateRestoreSupported(): Boolean = true
  }
}
