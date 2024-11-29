// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.laf.darcula.DarculaNewUIUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.util.height
import com.intellij.ui.util.width
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.border.Border

@ApiStatus.Internal
class ShadowJava2DBorder(private val arc: Int, private val background: Color, private val borderColor: Color?) : Border {

  private val shadowJava2DPainter = ShadowJava2DPainter(ShadowJava2DPainter.Type.NOTIFICATION, arc)

  override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g!!.create() as Graphics2D

    try {
      val insets = getBorderInsets(c)
      val lw = DarculaUIUtil.LW.get()
      shadowJava2DPainter.paintShadow(g2, x, y, width, height)

      g2.color = background
      val rect = Rectangle(x + insets.left - lw, y + insets.top - lw, width - insets.width + lw * 2, height - insets.height + lw * 2)

      if (borderColor == null) {
        DarculaNewUIUtil.fillRoundedRectangle(g2, rect, background, arc.toFloat())
      } else {
        DarculaNewUIUtil.fillInsideComponentBorder(g2, rect, background, arc.toFloat())
        DarculaNewUIUtil.drawRoundedRectangle(g2, rect, borderColor, arc.toFloat(), DarculaUIUtil.LW.get())
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    return shadowJava2DPainter.getInsets()
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}