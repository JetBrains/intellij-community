// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow.xNext.island

import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.wm.impl.IdeBackgroundUtil
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import fleet.util.logging.logger
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.AbstractBorder
import javax.swing.border.Border
import kotlin.math.max

internal class XNextToolWindowHolder private constructor(): JPanel() {
  companion object {
    @JvmStatic
    fun create(): JComponent = XNextToolWindowHolder().apply {
      border = JRoundedCornerBorder()
      ClientProperty.putRecursive(this, IdeBackgroundUtil.NO_BACKGROUND, true)
    }
  }

  override fun setBorder(border: Border?) {
    if (border !is JRoundedCornerBorder) {
      logger<XNextToolWindowHolder>().error {
        "Border type is invalid. Expected JRoundedCornerBorder, but received: ${border?.javaClass?.name ?: "null"}."
      }
      return 
    }
    super.setBorder(border)
  }

  override fun isOpaque(): Boolean {
    return true
  }
}

private class JRoundedCornerBorder : AbstractBorder() {
  companion object {
    const val THICKNESS: Int = 10
    const val ARC: Int = 25
  }

  fun getBorderShape(width: Int, height: Int): Shape {
    val th = JBUI.scale(THICKNESS).toDouble()
    val innerArc = max((ARC - th), 0.0).toInt()
    return RoundRectangle2D.Double(th, th,
                                   (width - (2 * th)), (height - (2 * th)),
                                   innerArc.toDouble(), innerArc.toDouble())
  }

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D

    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      val extArea = Area(Rectangle(0, 0, width, height))
      extArea.subtract(Area(getBorderShape(width, height)))
      g2.color = InternalUICustomization.getInstance().getCustomMainBackgroundColor() ?: c.background

      g2.fill(extArea)

      g2.color = c.parent.background
      val th = JBUI.scale(THICKNESS).toFloat()
      g2.stroke = BasicStroke(th, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
      val borderShape = getBorderShape(width, height)
      g2.draw(borderShape)
    } finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    val th = JBUI.scale(THICKNESS)
    return JBInsets(th, th, th, th)
  }

  override fun getBorderInsets(c: Component?, insets: Insets): Insets {
    insets.bottom = JBUI.scale(THICKNESS)
    insets.right = insets.bottom
    insets.top = insets.right
    insets.left = insets.top
    return insets
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}