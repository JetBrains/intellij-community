// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.text.JTextComponent
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

internal object StyledTextPaneUtils {
  private val arc: Int
    get() = JBUI.scale(4)
  private val indent: Int
    get() = JBUI.scale(2)

  fun setCommonTextAttributes(attributes: SimpleAttributeSet) {
    val font = JBFont.label()
    StyleConstants.setFontFamily(attributes, font.fontName)
    StyleConstants.setFontSize(attributes, font.size)
    StyleConstants.setForeground(attributes, JBUI.CurrentTheme.Label.foreground())
  }

  fun JTextComponent.drawRectangleAroundText(startOffset: Int, endOffset: Int, g: Graphics, needColor: Color, fill: Boolean) {
    val g2d = g as Graphics2D
    val rectangleStart = modelToView2D(startOffset)
    val rectangleEnd = modelToView2D(endOffset)
    val color = g2d.color
    val fontSize = JBUI.Fonts.label().size2D

    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    val shift = if (SystemInfo.isMac) 1f else 2f
    val r2d = RoundRectangle2D.Double(rectangleStart.x - 2 * indent, rectangleStart.y - indent + JBUIScale.scale(shift),
                                      rectangleEnd.x - rectangleStart.x + 4 * indent, (fontSize + 2 * indent).toDouble(),
                                      arc.toDouble(), arc.toDouble())

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