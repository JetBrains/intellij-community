// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.builder.components

import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import javax.swing.border.LineBorder

@ApiStatus.Internal
@ApiStatus.Experimental
internal class RoundedLineBorderWithBackground(
  color: Color,
  private val bgColor: Color,
  private val arcSize: Int = JBUI.scale(10),
  thickness: Int = JBUI.scale(1),
) : LineBorder(color, thickness) {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g as Graphics2D

    val oldAntialiasing = g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING)
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val oldColor = g2.color

    g2.color = bgColor
    g2.fillRoundRect(x + thickness - 1, y + thickness - 1,
                     width - thickness - thickness + 1,
                     height - thickness - thickness + 1,
                     arcSize, arcSize)

    g2.color = lineColor
    for (i in 0 until thickness) {
      g2.drawRoundRect(x + i, y + i, width - i - i - 1, height - i - i - 1, arcSize, arcSize)
    }

    g2.color = oldColor
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, oldAntialiasing)
  }

  override fun getBorderInsets(c: Component, insets: Insets) = JBUI.emptyInsets()
}