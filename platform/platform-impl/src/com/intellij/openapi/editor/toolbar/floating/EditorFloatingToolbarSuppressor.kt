// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.toolbar.floating

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Extension point to suppress the display of editor floating toolbars.
 * If any extension returns true from [isSuppressed], the toolbar will not be shown.
 */
interface EditorFloatingToolbarSuppressor {
  /**
   * Determines if the floating toolbar should be suppressed.
   */
  fun isSuppressed(
    provider: FloatingToolbarProvider,
    dataContext: DataContext,
  ): Boolean

  companion object {
    val EP_NAME: ExtensionPointName<EditorFloatingToolbarSuppressor> =
      ExtensionPointName.create("com.intellij.editorFloatingToolbarProviderSuppressor")
  }
}