package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * This extension point allows to specify if editor can be closed by tabs limit
 */
@ApiStatus.Internal
@ApiStatus.Experimental
interface EditorAutoClosingHandler {
  companion object {
    val EP_NAME = ExtensionPointName.create<EditorAutoClosingHandler>("com.intellij.editorAutoClosingHandler")
  }

  fun isClosingAllowed(composite : EditorComposite): Boolean
}