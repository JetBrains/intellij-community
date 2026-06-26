// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ApplicationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Graphics
import javax.swing.JComponent

internal class EditorEmptyTextComponentProvider : EditorEmptyStateComponentProvider {
  override fun getKind(): EditorEmptyStateComponentProvider.Kind = EditorEmptyStateComponentProvider.Kind.FALLBACK

  override fun isAvailable(splitters: EditorsSplitters): Boolean {
    return EditorEmptyTextPainter.isEnabled() && EditorEmptyTextProvider.EP_NAME.extensionList.isNotEmpty()
  }

  override suspend fun createComponent(splitters: EditorsSplitters): JComponent {
    return withContext(Dispatchers.EDT) {
      EditorEmptyTextComponent(splitters)
    }
  }
}

private class EditorEmptyTextComponent(private val splitters: EditorsSplitters) : JComponent() {
  private val painter = ApplicationManager.getApplication().getService(EditorEmptyTextPainter::class.java)

  init {
    name = EDITOR_EMPTY_TEXT_COMPONENT_NAME
    isOpaque = false
    isFocusable = false
  }

  override fun paintComponent(g: Graphics) {
    super.paintComponent(g)
    painter.paintEmptyText(splitters, this, g)
  }
}

internal const val EDITOR_EMPTY_TEXT_COMPONENT_NAME: String = "EditorEmptyTextComponent"
