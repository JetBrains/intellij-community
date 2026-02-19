// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Graphics
import java.awt.Insets
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.border.Border

internal class CommitInputBorder(
  editor: EditorEx,
  private val borderOwner: JComponent,
) : Border, ErrorBorderCapable {
  init {
    editor.addFocusListener(object : FocusChangeListener {
      override fun focusGained(editor: Editor) = repaintOwner()
      override fun focusLost(editor: Editor) = repaintOwner()
      private fun repaintOwner() {
        borderOwner.repaint()
      }
    })
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val r = Rectangle(x, y, width, height)
    JBInsets.removeFrom(r, DarculaUIUtil.paddings())

    DarculaNewUIUtil.fillInsideComponentBorder(g, r, c.background)
    val enabled = c.isEnabled
    val hasFocus = UIUtil.isFocusAncestor(c)
    DarculaNewUIUtil.paintComponentBorder(g, r, DarculaUIUtil.getOutline(c as JComponent), hasFocus, enabled)
  }

  override fun getBorderInsets(c: Component): Insets {
    return JBInsets(COMMIT_BORDER_INSET).asUIResource()
  }
  override fun isBorderOpaque(): Boolean = true

  companion object {
    const val COMMIT_BORDER_INSET: Int = 3
  }
}