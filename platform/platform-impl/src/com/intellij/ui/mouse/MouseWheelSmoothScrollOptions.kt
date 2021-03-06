// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mouse

import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.CubicCurve2D
import java.awt.geom.Point2D
import javax.swing.JComponent

internal class MouseWheelSmoothScrollOptionsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val settings = UISettings.instance.state
    val points = settings.animatedScrollingCurvePoints
    val myBezierPainter = BezierPainter(
      (points shr 24 and 0xFF) / 200.0,
      (points shr 16 and 0xFF) / 200.0,
      (points shr 8 and 0xFF) / 200.0,
      (points and 0xFF) / 200.0
    )
    myBezierPainter.minimumSize = Dimension(300, 200)

    dialog(
      title = IdeBundle.message("title.smooth.scrolling.options"),
      panel = panel {
        lateinit var c: CellBuilder<JBCheckBox>
        row {
          c = checkBox(IdeBundle.message("checkbox.smooth.scrolling.animated"), settings::animatedScrolling)
        }
        row {
          row(IdeBundle.message("label.smooth.scrolling.duration")) {
            cell {
              spinner(settings::animatedScrollingDuration, 0, 2000, 50).enableIf(c.selected)
              label(IdeBundle.message("label.milliseconds"))
            }
          }
        }
        row {
          myBezierPainter(CCFlags.grow).enableIf(c.selected)
        }
      }
    ).showAndGet().let {
      if (it) {
        val (x1, y1) = myBezierPainter.firstControlPoint
        val (x2, y2) = myBezierPainter.secondControlPoint
        var targetValue = 0
        targetValue += (x1 * 200).toInt() shl 24 and 0xFF000000.toInt()
        targetValue += (y1 * 200).toInt() shl 16 and 0xFF0000
        targetValue += (x2 * 200).toInt() shl 8 and 0xFF00
        targetValue += (y2 * 200).toInt() and 0xFF
        settings.animatedScrollingCurvePoints = targetValue
      }
    }

  }
}

private class BezierPainter(x1: Double, y1: Double, x2: Double, y2: Double) : JComponent() {

  private val controlSize = 10
  private val gridColor = JBColor(Color(0xF0F0F0), Color(0x313335))
  var firstControlPoint: Point2D.Double = Point2D.Double(x1, y1)
  var secondControlPoint: Point2D.Double = Point2D.Double(x2, y2)

  init {
    val value = object : MouseAdapter() {
      var i = 0

      override fun mouseDragged(e: MouseEvent) {
        val point = e.point
        when (i) {
          1 -> firstControlPoint = fromScreenXY(point)
          2 -> secondControlPoint = fromScreenXY(point)
        }
        repaint()
      }

      override fun mouseMoved(e: MouseEvent) = with (e.point ) {
        i = when {
          this intersects firstControlPoint -> 1
          this intersects secondControlPoint -> 2
          else -> 0
        }
        if (i != 0) {
          cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        } else {
          cursor = Cursor.getDefaultCursor()
        }
      }

      private infix fun Point.intersects(point: Point2D.Double) : Boolean {
        val xy = toScreenXY(point)
        return xy.x - controlSize / 2 <= x && x <= xy.x + controlSize / 2 &&
               xy.y - controlSize / 2 <= y && y <= xy.y + controlSize / 2
      }
    }
    addMouseMotionListener(value)
    addMouseListener(value)
  }

  private fun toScreenXY(point: Point2D) : Point = bounds.let { b ->
    return Point((point.x * b.width).toInt(), b.height - (point.y * b.height).toInt())
  }

  private fun fromScreenXY(point: Point) : Point2D.Double = bounds.let { b ->
    val x = minOf(maxOf(0, point.x), bounds.width).toDouble() / bounds.width
    val y = minOf(maxOf(0, point.y), bounds.height).toDouble() / bounds.height
    return Point2D.Double(x, 1.0 - y)
  }

  override fun paintComponent(g: Graphics) {
    val bounds = bounds

    val g2d = g.create() as Graphics2D

    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED)
    g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g2d.color = JBColor.background()
    g2d.fillRect(0, 0, bounds.width, bounds.height)

    g2d.color = gridColor
    for (i in 0..4) {
      g2d.drawLine(bounds.width * i / 4, 0, bounds.width * i / 4, bounds.height)
      g2d.drawLine(0,bounds.height * i / 4, bounds.width, bounds.height * i / 4)
    }

    val x0 = 0.0
    val y0 = bounds.height.toDouble()
    val (x1, y1) = toScreenXY(firstControlPoint)
    val (x2, y2) = toScreenXY(secondControlPoint)
    val x3 = bounds.width.toDouble()
    val y3 = 0.0

    val bez = CubicCurve2D.Double(x0, y0, x1,  y1, x2, y2, x3, y3)

    g2d.color = JBColor.foreground()
    g2d.stroke = BasicStroke(2f)
    g2d.draw(bez)
    g2d.stroke = BasicStroke(1f)
    g2d.color = when {
      isEnabled -> Color(151, 118, 169)
      JBColor.isBright() -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    g2d.fillOval(x1.toInt() - controlSize / 2, y1.toInt() - controlSize / 2, controlSize, controlSize)
    g2d.drawLine(x0.toInt(), y0.toInt(), x1.toInt(), y1.toInt())
    g2d.color = when {
      isEnabled -> Color(208, 167, 8)
      JBColor.isBright() -> JBColor.LIGHT_GRAY
      else -> JBColor.GRAY
    }
    g2d.fillOval(x2.toInt() - controlSize / 2, y2.toInt() - controlSize / 2, controlSize, controlSize)
    g2d.drawLine(x2.toInt(), y2.toInt(), x3.toInt(), y3.toInt())

  }
}

private operator fun Point2D.component1() = x
private operator fun Point2D.component2() = y