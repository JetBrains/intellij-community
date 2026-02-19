// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.stacktrace.editor

import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import org.jetbrains.idea.devkit.stacktrace.DevKitStackTraceBundle

/**
 * Text and stacktrace preview editor.
 */
internal class StackTraceEditorWithPreview(editor: TextEditor, preview: StackTraceFileEditor)
  : TextEditorWithPreview(editor, preview, DevKitStackTraceBundle.message("stack.trace"), Layout.SHOW_EDITOR_AND_PREVIEW, false) {

  init {
    preview.setMainEditor(editor.getEditor())
  }

  override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
    if (newValue == Layout.SHOW_PREVIEW) {
      myPreview.preferredFocusedComponent?.requestFocus() ?: myPreview.component.requestFocus()
    }
  }
}