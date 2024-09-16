// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex.commit

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import java.awt.*
import java.awt.geom.Rectangle2D
import javax.swing.JComponent
import javax.swing.border.Border

internal class CommitInputBorder(
  private val editor: EditorEx,
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
    val hasFocus = editor.contentComponent.hasFocus()
    val r = Rectangle(x, y, width, height)
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
      JBInsets.removeFrom(r, JBUI.insets(1))
      g2.translate(r.x, r.y)
      val bw = DarculaUIUtil.BW.float
      val outer = Rectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2)
      g2.color = c.background
      g2.fill(outer)

      if (editor.contentComponent.isEnabled && editor.contentComponent.isVisible) {
        val op = DarculaUIUtil.getOutline(c as JComponent)
        val hasFocusInPopup = hasFocus //&& !AIChatPopup.chatInPopupEnabled(parent.mode)
        if (op == null) {
          g2.color = if (hasFocusInPopup) JBUI.CurrentTheme.Focus.focusColor()
          else DarculaUIUtil.getOutlineColor(editor.contentComponent.isEnabled, false)
        }
        else {
          op.setGraphicsColor(g2, hasFocus)
        }
        DarculaUIUtil.doPaint(g2, r.width, r.height, 5f, if (hasFocus) 1f else 0.5f, true)
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component): Insets = JBInsets.create(4, 8).asUIResource()
  override fun isBorderOpaque(): Boolean = true
}

