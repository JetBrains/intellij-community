// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.ui.scale.JBUIScale
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.text.JTextComponent
import javax.swing.text.Position

internal object StyledTextPaneUtils {
  fun JTextComponent.drawRectangleAroundText(startOffset: Int, endOffset: Int, g: Graphics, needColor: Color, font: Font, fill: Boolean) {
    val g2d = g as Graphics2D
    // modelToView2D from JTextComponent returns rectangle of the text row that is affected by the line spacing
    // so get the rectangle using other way
    val rect = ui.getRootView(this)
      .modelToView(startOffset, Position.Bias.Forward, endOffset, Position.Bias.Backward, this.bounds)
      .let { SwingUtilities.convertRectangle(this.parent, it.bounds, this) }
      .bounds2D
    val fontHeight = g2d.getFontMetrics(font).height
    if (rect.height > fontHeight) {
      return  // there is line break between startOffset and endOffset
    }

    val color = g2d.color
    g2d.color = needColor
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    
    val verticalIndent = JBUIScale.scale(1.5f).toDouble()
    val horizontalIndent = JBUIScale.scale(4f).toDouble()
    val arc = JBUIScale.scale(8f).toDouble()
    val r2d = RoundRectangle2D.Double(rect.x - horizontalIndent, rect.y - verticalIndent,
                                      rect.width + 2 * horizontalIndent, rect.height + 2 * verticalIndent,
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