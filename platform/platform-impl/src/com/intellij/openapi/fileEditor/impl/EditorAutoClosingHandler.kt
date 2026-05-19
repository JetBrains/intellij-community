package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Allows editor tabs to prevent automatic closing when the tab limit is exceeded.
 * Tabs that are not allowed to close are also excluded from the tab-limit count.
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface EditorAutoClosingHandler {
  companion object {
    val EP_NAME = ExtensionPointName.create<EditorAutoClosingHandler>("com.intellij.editorAutoClosingHandler")
  }

  fun isClosingAllowed(composite : EditorComposite): Boolean
}