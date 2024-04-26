// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.ui.scale.JBUIScale
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.JTextComponent

internal object StyledTextPaneUtils {
  fun JTextComponent.drawRectangleAroundText(startOffset: Int,
                                             endOffset: Int,
                                             g: Graphics,
                                             needColor: Color,
                                             textFont: Font,
                                             delimiterFont: Font,
                                             fill: Boolean,
                                             verticalIndent: Float) {
    val g2d = g as Graphics2D
    val startRect = modelToView2D(startOffset)
    val endRect = modelToView2D(endOffset)
    val textFontMetrics = g2d.getFontMetrics(textFont)
    val delimiterFontMetrics = g2d.getFontMetrics(delimiterFont)

    val color = g2d.color
    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

    val scaledVerticalIndent = JBUIScale.scale(verticalIndent).toDouble()
    val horizontalIndent = delimiterFontMetrics.stringWidth(ShortcutsRenderingUtil.SHORTCUT_PART_SEPARATOR) * 4 / 11.0
    val arc = JBUIScale.scale(8f).toDouble()
    val r2d = RoundRectangle2D.Double(startRect.x - horizontalIndent, startRect.y - scaledVerticalIndent,
                                      endRect.x - startRect.x + 2 * horizontalIndent, textFontMetrics.height + 2 * scaledVerticalIndent,
                                      arc, arc)

    if (fill) g2d.fill(r2d) else g2d.draw(r2d)
    g2d.color = color
  }

  fun JTextPane.insertIconWithFixedHeight(icon: Icon, fixedHeight: Int) {
    insertIcon(object : Icon {
      override fun getIconWidth() = icon.iconWidth

      override fun getIconHeight() = fixedHeight

      override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        icon.paintIcon(c, g, x, y)
      }
    })
  }
}