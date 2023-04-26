// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons.Ide.RoundShadow
import com.intellij.util.ui.JBValue
import com.intellij.util.ui.JBValue.UIInteger
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Rectangle
import java.awt.geom.RoundRectangle2D

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