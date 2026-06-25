// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
interface EditorEmptyStateComponentProvider {
  /**
   * Called asynchronously by the editor host. Implementations should choose their dispatcher explicitly.
   */
  suspend fun createComponent(splitters: EditorsSplitters): JComponent?

  fun disposeComponent(component: JComponent) {
  }

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorEmptyStateComponentProvider> =
      ExtensionPointName("com.intellij.editorEmptyStateComponentProvider")
  }
}