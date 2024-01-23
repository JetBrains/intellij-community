// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.math.max

class RoundedPanel(radius: Int) : JPanel(BorderLayout())  {
  val contentPanel: JPanel = RoundedJPanel(radius)

  init {
    add(contentPanel, BorderLayout.CENTER)
  }

  private class RoundedJPanel(radius: Int) : JPanel() {

    private val radius = radius.toFloat()

    override fun paintComponent(g: Graphics) {
      val g2d = g as Graphics2D
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

      g2d.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), radius.toDouble(), radius.toDouble()))
      super.paintComponent(g2d)
    }
  }
}

class FilledRoundedBorder(private val myColor: Color,
                                  private val myArcSize: Int,
                                  private val myThickness: Int,
                                  private val myThinBorder: Boolean) : Border {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val config = GraphicsUtil.setupAAPainting(g)

    g.color = myColor

    val thickness = JBUI.scale(if (myThinBorder) 1 else myThickness)
    val arcSize = JBUI.scale(myArcSize)
    val area = Area(
      RoundRectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), arcSize.toDouble(), arcSize.toDouble()))
    val innerArc = max((arcSize - thickness).toDouble(), 0.0).toInt()
    area.subtract(Area(RoundRectangle2D.Double((x + thickness).toDouble(), (y + thickness).toDouble(),
                                               (width - 2 * thickness).toDouble(), (height - 2 * thickness).toDouble(),
                                               innerArc.toDouble(), innerArc.toDouble())))
    (g as Graphics2D).fill(area)

    config.restore()
  }

  override fun getBorderInsets(c: Component): Insets {
    return JBUI.insets(myThickness)
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}