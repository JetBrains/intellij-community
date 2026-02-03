// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons.Ide.RoundShadow
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBValue.UIInteger
import java.awt.*
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent

/**
 * @author Alexander Lobas
 */
class NotificationBalloonRoundShadowBorderProvider(fillColor: Color, borderColor: Color) : NotificationBalloonShadowBorderProvider(
  fillColor = fillColor,
  borderColor = borderColor,
  topIcon = RoundShadow.Top,
  leftIcon = RoundShadow.Left,
  bottomIcon = RoundShadow.Bottom,
  rightIcon = RoundShadow.Right,
  topLeftIcon = RoundShadow.TopLeft,
  topRightIcon = RoundShadow.TopRight,
  bottomLeftIcon = RoundShadow.BottomLeft,
  bottomRightIcon = RoundShadow.BottomRight,
) {
  companion object {
    @JvmField
    val CORNER_RADIUS: JBValue = UIInteger("Notification.arc", 12)
  }

  private val java2DPainter = ShadowJava2DPainter(ShadowJava2DPainter.Type.NOTIFICATION, JBUI.scale(6))

  fun hideSide(top: Boolean, bottom: Boolean) {
    java2DPainter.hideSide(top, bottom)
  }

  override fun getInsets(): Insets {
    return java2DPainter.getInsets()
  }

  override fun paintShadow(component: JComponent, g: Graphics) {
    java2DPainter.paintShadow(g as Graphics2D, 0, 0, component.width, component.height)
  }

  override fun paintBorder(bounds: Rectangle, g: Graphics2D) {
    val cornerRadius = CORNER_RADIUS.get()
    g.color = fillColor
    g.fill(RoundRectangle2D.Double(bounds.x.toDouble(), bounds.y.toDouble(), bounds.width.toDouble(), bounds.height.toDouble(),
                                   cornerRadius.toDouble(), cornerRadius.toDouble()))
    g.color = borderColor
    g.draw(RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, (bounds.width - 1).toDouble(), (bounds.height - 1).toDouble(),
                                   cornerRadius.toDouble(), cornerRadius.toDouble()))
  }
}