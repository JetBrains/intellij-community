// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.tabInEditor

import com.intellij.openapi.fileEditor.impl.EditorAutoClosingHandler
import com.intellij.openapi.fileEditor.impl.EditorComposite

internal class ToolWindowEditorTabAutoClosingHandler : EditorAutoClosingHandler {
  override fun isClosingAllowed(composite: EditorComposite): Boolean {
    return composite.file !is ToolWindowEditorTabFile
  }
}
