// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.math.max

class RoundedPanel private constructor(val unscaledRadius: Int = RADIUS) : JPanel(BorderLayout()) {
  companion object {
    const val RADIUS = 20
    const val thickness = 2
    val SELECTED_BORDER_COLOR = JBColor(0x3574F0, 0x3574F0)
    val BORDER_COLOR = JBColor(0xD3D5DB, 0x43454A)

    fun createRoundedPane(): RoundedPanel {
      return RoundedPanel(RADIUS)
    }
  }

  fun createBorder(borderColor: Color,
                   myThickness: Int = thickness): Border {
    return RoundedBorder(borderColor, myThickness, this)
  }

  val contentPanel: JPanel = RoundedJPanel(unscaledRadius)

  init {
    add(contentPanel, BorderLayout.CENTER)
  }

  private class RoundedJPanel(radius: Int) : JPanel() {

    private val radius = JBUI.scale(radius)

    override fun paintComponent(g: Graphics) {
      val g2d = g as Graphics2D
      val config = GraphicsUtil.setupAAPainting(g)
      g2d.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), radius.toDouble(), radius.toDouble()))
      super.paintComponent(g2d)

      config.restore()
    }
  }
}

private class RoundedBorder(borderColor: Color,
                            myThickness: Int,
                            panel: RoundedPanel
) : FilledRoundedBorder({ borderColor }, { panel.background }, myThickness, panel.unscaledRadius)

open class FilledRoundedBorder(private val borderColor: () -> Color,
                               private val backgroundColor: () -> Color,
                               private val thickness: Int,
                               private val unscaledRadius: Int
) : Border {
  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D

    val config = GraphicsUtil.setupAAPainting(g)

    val arcSize = JBUI.scale(unscaledRadius)
    val area = Area(
      RoundRectangle2D.Double(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble(), arcSize.toDouble(), arcSize.toDouble()))

    val bkgArea = Area(
      RoundRectangle2D.Double(x.toDouble() + (thickness / 2), y.toDouble() + (thickness / 2), (width - thickness).toDouble(),
                              (height - thickness).toDouble(), arcSize.toDouble(), arcSize.toDouble()))

    g.color = backgroundColor()
    g.fill(bkgArea)

    g.color = borderColor()

    val innerArc = max((arcSize - thickness).toDouble(), 0.0).toInt()
    val innerArea = Area(RoundRectangle2D.Double((x + thickness).toDouble(), (y + thickness).toDouble(),
                                                 (width - 2 * thickness).toDouble(), (height - 2 * thickness).toDouble(),
                                                 innerArc.toDouble(), innerArc.toDouble()))
    area.subtract(innerArea)
    g.fill(area)

    config.restore()
  }

  override fun getBorderInsets(c: Component): Insets {
    return JBUI.insets(thickness)
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}