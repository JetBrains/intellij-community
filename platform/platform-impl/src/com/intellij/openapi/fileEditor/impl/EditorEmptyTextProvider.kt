// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import javax.swing.JComponent

@ApiStatus.Internal
interface EditorEmptyTextProvider {
  fun appendEmptyText(splitters: JComponent, sink: EditorEmptyTextSink)

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<EditorEmptyTextProvider> =
      ExtensionPointName.create("com.intellij.editorEmptyTextProvider")
  }
}

@ApiStatus.Internal
interface EditorEmptyTextSink {
  fun appendLine(@Nls line: String)

  fun appendAction(@Nls action: String, shortcut: String?)

  fun appendActionWithShortcuts(@Nls action: String, @NonNls actionId: String)

  fun appendActionWithFirstKeyboardShortcut(@Nls action: String, @NonNls actionId: String)

  fun appendToolWindow(@Nls action: String, @NonNls toolWindowId: String)
}
