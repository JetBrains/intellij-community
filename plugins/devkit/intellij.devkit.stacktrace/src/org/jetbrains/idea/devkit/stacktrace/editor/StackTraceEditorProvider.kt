// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.stacktrace.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider

internal class StackTraceEditorProvider : TextEditorWithPreviewProvider(StackTraceFileEditorProvider()) {

  override fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    require(secondEditor is StackTraceFileEditor) { "Secondary editor should be StackTraceFileEditor" }
    return StackTraceEditorWithPreview(firstEditor, secondEditor)
  }

}