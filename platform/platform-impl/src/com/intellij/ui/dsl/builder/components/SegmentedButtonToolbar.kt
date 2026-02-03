// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.actionSystem.ActionButtonComponent
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.awt.Component
import java.awt.GradientPaint
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Insets
import java.awt.Paint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.border.Border
import kotlin.math.max

/**
 * @param subButton determines border target: true for button inside segmented button, false for segmented button
 */
@ApiStatus.Internal
internal fun getSegmentedButtonBorderPaint(segmentedButton: Component, subButton: Boolean): Paint {
  if (!segmentedButton.isEnabled) {
    return JBUI.CurrentTheme.Button.disabledOutlineColor()
  }

  if (segmentedButton.hasFocus()) {
    return JBUI.CurrentTheme.Button.focusBorderColor(false)
  }
  else {
    if (subButton) {
      return GradientPaint(0f, 0f, JBUI.CurrentTheme.SegmentedButton.SELECTED_START_BORDER_COLOR,
                           0f, segmentedButton.height.toFloat(), JBUI.CurrentTheme.SegmentedButton.SELECTED_END_BORDER_COLOR)
    }
    else {
      return GradientPaint(0f, 0f, JBUI.CurrentTheme.Button.buttonOutlineColorStart(false),
                           0f, segmentedButton.height.toFloat(), JBUI.CurrentTheme.Button.buttonOutlineColorEnd(false))
    }
  }
}

@ApiStatus.Internal
internal fun paintBorder(g: Graphics2D, r: Rectangle) {
  val border = Path2D.Float(Path2D.WIND_EVEN_ODD)
  val lw = DarculaUIUtil.LW.float
  var arc = DarculaUIUtil.BUTTON_ARC.float
  border.append(RoundRectangle2D.Float(r.x.toFloat(), r.y.toFloat(), r.width.toFloat(), r.height.toFloat(), arc, arc), false)
  arc = max(arc - lw, 0f)
  border.append(RoundRectangle2D.Float(r.x + lw, r.y + lw, r.width - lw * 2, r.height - lw * 2, arc, arc), false)
  g.fill(border)
}

@ApiStatus.Internal
internal class SegmentedButtonBorder : Border {

  override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
    val g2 = g.create() as Graphics2D
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE)
      g2.translate(x, y)
      val r = Rectangle(0, 0, width, height)
      val arc = DarculaUIUtil.BUTTON_ARC.float
      var outline = DarculaUIUtil.getOutline(c as JComponent)
      if (outline == null && c.hasFocus()) {
        outline = DarculaUIUtil.Outline.focus
      }
      if (outline == null) {
        g2.paint = getSegmentedButtonBorderPaint(c, false)
        JBInsets.removeFrom(r, JBUI.insets(DarculaUIUtil.BW.unscaled.toInt()))
        paintBorder(g2, r)
      }
      else {
        DarculaUIUtil.paintOutlineBorder(g2, r.width, r.height, arc, true, c.hasFocus(), outline)
      }
    }
    finally {
      g2.dispose()
    }
  }

  override fun getBorderInsets(c: Component?): Insets {
    val unscaledSize = DarculaUIUtil.BW.unscaled + DarculaUIUtil.LW.unscaled
    return JBUI.insets(unscaledSize.toInt()).asUIResource()
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}

@ApiStatus.Internal
internal object SegmentedButtonLook : IdeaActionButtonLook() {

  override fun paintBorder(g: Graphics, component: JComponent, state: Int) {
    // Border is painted in parent
  }

  override fun getStateBackground(component: JComponent, state: Int): Color? {
    if (!component.isEnabled) {
      return if (component.isBackgroundSet) component.background else null
    }

    val focused = component.parent?.hasFocus() == true

    return when (state) {
      ActionButtonComponent.POPPED -> JBUI.CurrentTheme.ActionButton.hoverBackground()
      ActionButtonComponent.PUSHED ->
        if (focused) JBUI.CurrentTheme.SegmentedButton.FOCUSED_SELECTED_BUTTON_COLOR
        else JBUI.CurrentTheme.SegmentedButton.SELECTED_BUTTON_COLOR
      else -> if (component.isBackgroundSet) component.background else null
    }
  }
}
