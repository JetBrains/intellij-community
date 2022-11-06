// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.rd.paint2DLine
import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.MacUIUtil
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.AbstractButton
import javax.swing.JComponent
import kotlin.math.roundToInt

internal class SegmentedBarPainter {
  companion object {
    private val strokeWidth = 1.0

    private val bw = 0f
    private val lw = DarculaUIUtil.LW.float

    private fun getGradientPaint(component: Component): Paint {
      return GradientPaint(bw, bw,
                           JBColor.namedColor(
                             "Button.startBorderColor",
                             JBColor.namedColor("Button.darcula.outlineStartColor", 0xbfbfbf)),
                           bw, component.height - (bw * 2),
                           JBColor
                             .namedColor("Button.endBorderColor",
                                         JBColor.namedColor(
                                           "Button.darcula.outlineEndColor",
                                           0xb8b8b8)))
    }

    fun paintButtonDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {
      if (!(c as AbstractButton).isContentAreaFilled) {
        return true
      }
      return paintDecorations(g, c, paint)
    }

    fun paintDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {

      val r = Rectangle(c.getSize())
      // JBInsets.removeFrom(r, if (DarculaButtonUI.isSmallVariant(c)) c.getInsets() else JBUI.insets(1))

      val g2 = g.create() as Graphics2D
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                            if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
        g2.translate(r.x, r.y)
        val arc = DarculaUIUtil.BUTTON_ARC.float
        val bw: Float = 0f //if (DarculaButtonUI.isSmallVariant(c)) 0f else DarculaUIUtil.BW.float

        if (c.isEnabled()) {
          g2.paint = paint

          c.getClientProperty(SegmentedActionToolbarComponent.CONTROL_BAR_PROPERTY)?.let {
            paintComponent(g2, Rectangle(c.getSize()), it.toString())
          } ?: g2.fill(RoundRectangle2D.Float(bw, bw, r.width - bw * 2, r.height - bw * 2, arc, arc))
        }
      }
      finally {
        g2.dispose()
      }
      return true
    }

  private fun paintComponent(g2: Graphics2D, r: Rectangle, position: String) {
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)
    g2.translate(r.x, r.y)
    val arc = DarculaUIUtil.BUTTON_ARC.float

    val wdth = r.width.toFloat()
    val offs = arc * 2

    val area = when (position) {
      SegmentedActionToolbarComponent.CONTROL_BAR_FIRST -> {
        val rightGap = (wdth / 3).roundToInt().toFloat()
        val area = Area(RoundRectangle2D.Float(bw, bw, wdth - bw, r.height.toFloat() - (2 * bw), arc, arc))
        area.add(Area(Rectangle2D.Float(wdth - rightGap, bw, rightGap, r.height.toFloat() - (2 * bw))))
        area
      }
      SegmentedActionToolbarComponent.CONTROL_BAR_MIDDLE -> {
        Area(Rectangle2D.Float(0f, bw, wdth, r.height.toFloat() - (2*bw)))
      }
      SegmentedActionToolbarComponent.CONTROL_BAR_LAST -> {
        val area = Area(RoundRectangle2D.Float(0f, bw, wdth - bw, r.height.toFloat() - (2*bw), arc, arc))
        area.add(Area(Rectangle2D.Float(0f, bw, offs, r.height.toFloat() - (2*bw))))
        area
      }
      else -> {
        Area(RoundRectangle2D.Float(0f, bw, wdth, r.height.toFloat() - (2*bw), arc, arc))
      }
    }

    g2.fill(area)
  }

  fun paintActionButtonBackground(g: Graphics, component: JComponent, state: Int) {
    if (state == ActionButtonComponent.NORMAL && !component.isBackgroundSet) return
    g.color = when (state) {
      ActionButtonComponent.NORMAL -> component.background
      ActionButtonComponent.PUSHED -> JBUI.CurrentTheme.ActionButton.pressedBackground()
      else -> JBUI.CurrentTheme.ActionButton.hoverBackground()
    }

    val rect = Rectangle(component.size)
    val insets = component.insets
    JBInsets.removeFrom(rect, JBUI.insets(insets.top, 0, insets.bottom, 0))
    component.getClientProperty(SegmentedActionToolbarComponent.CONTROL_BAR_PROPERTY)?.let {
      paintComponent(g as Graphics2D, Rectangle(component.getSize()), it.toString())
    } ?: g.fillRect(rect.x, rect.y, rect.width, rect.height)
  }

  fun paintActionBarBorder(component: JComponent, g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)


      val paint = getGradientPaint(component)

      for(i in 0 until component.componentCount - 1) {
        val comp = component.getComponent(i)
        val bounds = comp.bounds

        g2.paint2DLine(bounds.maxX - strokeWidth, bw.toDouble(), bounds.maxX - strokeWidth, (component.height - (bw * 2)).toDouble(),
                       LinePainter2D.StrokeType.INSIDE,
                       strokeWidth,
                       JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
      }

      var arc = DarculaUIUtil.BUTTON_ARC.float

      val border: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(RoundRectangle2D.Float(bw, bw, component.width - (bw * 2), component.height - (bw * 2), arc, arc), false)

      arc = if (arc > lw) arc - lw else 0.0f
      border.append(RoundRectangle2D.Float(bw + lw, bw + lw, component.width - ((bw + lw) * 2), component.height - ((bw + lw) * 2), arc,
                                           arc), false)

      g2.paint = paint
      g2.fill(border)
    }
    finally {
      g2.dispose()
    }
  }

  private fun componentBorder(g: Graphics, h: Int, shape: Shape) {
    val g2 = g.create() as Graphics2D
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)


    val paint = GradientPaint(bw, bw,
                              JBColor.namedColor(
                                "Button.startBorderColor",
                                JBColor.namedColor("Button.darcula.outlineStartColor", 0xbfbfbf)),
                              bw, h - (bw * 2),
                              JBColor
                                .namedColor("Button.endBorderColor",
                                            JBColor.namedColor(
                                              "Button.darcula.outlineEndColor",
                                              0xb8b8b8)))

    val border: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
    border.append(shape, true)
    g2.paint = paint
    g2.draw(border)
  }

  fun paintActionBarBackground(component: JComponent, g: Graphics) {
    val g2 = g.create() as Graphics2D
    try {

      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                          if (MacUIUtil.USE_QUARTZ) RenderingHints.VALUE_STROKE_PURE else RenderingHints.VALUE_STROKE_NORMALIZE)


      val arc = DarculaUIUtil.BUTTON_ARC.float

      val border: Path2D = Path2D.Float(Path2D.WIND_EVEN_ODD)
      border.append(RoundRectangle2D.Float(bw, bw, component.width - (bw * 2), component.height - (bw * 2), arc, arc), false)

      g2.color = JBColor.namedColor("Panel.background", Gray.xCD)
      g2.fill(border)

    }
    finally {
      g2.dispose()
    }
  }
  }
}