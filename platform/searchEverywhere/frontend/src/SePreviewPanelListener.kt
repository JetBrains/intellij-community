// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * A very internal hack for fixing IJPL-237895. A subject to removal. Please do not use it.
 */
@ApiStatus.Internal
@FunctionalInterface
interface SePreviewPanelListener {
  companion object {
    val EP: ExtensionPointName<SePreviewPanelListener> = ExtensionPointName.create("com.intellij.searchEverywhere.previewPanelListener")
  }

  /**
   * Called, when new [editor] is used to show a preview in Search Everywhere preview tab
   */
  fun onNewPreviewEditor(editor: Editor)
  fun onItemSelected()
}