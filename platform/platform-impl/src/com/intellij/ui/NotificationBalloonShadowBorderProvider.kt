// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.IconLoader.getIconSnapshot
import com.intellij.openapi.util.IconLoader.toImage
import com.intellij.ui.BalloonImpl.ShadowBorderProvider
import com.intellij.ui.scale.ScaleContext.Companion.create
import com.intellij.util.ui.drawImage
import java.awt.*
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon
import javax.swing.JComponent

/**
 * @author Alexander Lobas
 */
open class NotificationBalloonShadowBorderProvider protected constructor(@JvmField protected val fillColor: Color,
                                                                         @JvmField protected val borderColor: Color,
                                                                         private val topIcon: Icon,
                                                                         private val leftIcon: Icon,
                                                                         private val bottomIcon: Icon,
                                                                         private val rightIcon: Icon,
                                                                         private val topLeftIcon: Icon,
                                                                         private val topRightIcon: Icon,
                                                                         private val bottomLeftIcon: Icon,
                                                                         private val bottomRightIcon: Icon) : ShadowBorderProvider {
  constructor(fillColor: Color, borderColor: Color) : this(fillColor, borderColor,
                                                           AllIcons.Ide.Shadow.Top, AllIcons.Ide.Shadow.Left, AllIcons.Ide.Shadow.Bottom,
                                                           AllIcons.Ide.Shadow.Right,
                                                           AllIcons.Ide.Shadow.TopLeft, AllIcons.Ide.Shadow.TopRight,
                                                           AllIcons.Ide.Shadow.BottomLeft, AllIcons.Ide.Shadow.BottomRight)

  override fun getInsets(): Insets {
    @Suppress("UseDPIAwareInsets")
    return Insets(topIcon.iconHeight, leftIcon.iconWidth, bottomIcon.iconHeight, rightIcon.iconWidth)
  }

  override fun paintShadow(component: JComponent, g: Graphics) {
    val width = component.width
    val height = component.height
    val topLeftWidth = topLeftIcon.iconWidth
    val topLeftHeight = topLeftIcon.iconHeight
    val topRightWidth = topRightIcon.iconWidth
    val topRightHeight = topRightIcon.iconHeight
    val bottomLeftWidth = bottomLeftIcon.iconWidth
    val bottomLeftHeight = bottomLeftIcon.iconHeight
    val bottomRightWidth = bottomRightIcon.iconWidth
    val bottomRightHeight = bottomRightIcon.iconHeight
    val rightWidth = rightIcon.iconWidth
    val bottomHeight = bottomIcon.iconHeight
    drawLine(component, g, topIcon, width, topLeftWidth, topRightWidth, 0, true)
    drawLine(component, g, bottomIcon, width, bottomLeftWidth, bottomRightWidth, height - bottomHeight, true)
    drawLine(component, g, leftIcon, height, topLeftHeight, bottomLeftHeight, 0, false)
    drawLine(component, g, rightIcon, height, topRightHeight, bottomRightHeight, width - rightWidth, false)
    topLeftIcon.paintIcon(component, g, 0, 0)
    topRightIcon.paintIcon(component, g, width - topRightWidth, 0)
    bottomRightIcon.paintIcon(component, g, width - bottomRightWidth, height - bottomRightHeight)
    bottomLeftIcon.paintIcon(component, g, 0, height - bottomLeftHeight)
  }

  override fun paintBorder(bounds: Rectangle, g: Graphics2D) {
    g.color = fillColor
    g.fill(Rectangle2D.Double(bounds.x.toDouble(), bounds.y.toDouble(), bounds.width.toDouble(), bounds.height.toDouble()))
    g.color = borderColor
    g.draw(RoundRectangle2D.Double(bounds.x + 0.5, bounds.y + 0.5, (bounds.width - 1).toDouble(), (bounds.height - 1).toDouble(), 3.0, 3.0))
  }

  override fun paintPointingShape(bounds: Rectangle,
                                  pointTarget: Point,
                                  position: Balloon.Position,
                                  g: Graphics2D) {
    val x: Int
    val y: Int
    var length: Int
    when (position) {
      Balloon.Position.above -> {
        length = insets.bottom
        x = pointTarget.x
        y = bounds.y + bounds.height + length
      }
      Balloon.Position.below -> {
        length = insets.top
        x = pointTarget.x
        y = bounds.y - length
      }
      Balloon.Position.atRight -> {
        length = insets.left
        x = bounds.x - length
        y = pointTarget.y
      }
      else -> {
        length = insets.right
        x = bounds.x + bounds.width + length
        y = pointTarget.y
      }
    }
    val p = Polygon()
    p.addPoint(x, y)
    length += 2
    when (position) {
      Balloon.Position.above -> {
        p.addPoint(x - length, y - length)
        p.addPoint(x + length, y - length)
      }
      Balloon.Position.below -> {
        p.addPoint(x - length, y + length)
        p.addPoint(x + length, y + length)
      }
      Balloon.Position.atRight -> {
        p.addPoint(x + length, y - length)
        p.addPoint(x + length, y + length)
      }
      else -> {
        p.addPoint(x - length, y - length)
        p.addPoint(x - length, y + length)
      }
    }
    g.color = fillColor
    g.fillPolygon(p)
    g.color = borderColor
    length -= 2
    when (position) {
      Balloon.Position.above -> {
        g.drawLine(x, y, x - length, y - length)
        g.drawLine(x, y, x + length, y - length)
      }
      Balloon.Position.below -> {
        g.drawLine(x, y, x - length, y + length)
        g.drawLine(x, y, x + length, y + length)
      }
      Balloon.Position.atRight -> {
        g.drawLine(x, y, x + length, y - length)
        g.drawLine(x, y, x + length, y + length)
      }
      else -> {
        g.drawLine(x, y, x - length, y - length)
        g.drawLine(x, y, x - length, y + length)
      }
    }
  }
}

private fun drawLine(component: JComponent,
                     g: Graphics,
                     icon: Icon,
                     fullLength: Int,
                     start: Int,
                     end: Int,
                     start2: Int,
                     horizontal: Boolean) {
  val length = fullLength - start - end
  val iconSnapshot = getIconSnapshot(icon)
  val image = toImage(iconSnapshot, create(component))
  val iconWidth = iconSnapshot.iconWidth
  val iconHeight = iconSnapshot.iconHeight
  if (horizontal) {
    drawImage(g = g,
              image = image!!,
              x = start,
              y = start2,
              dw = length,
              dh = iconHeight,
              sourceBounds = Rectangle(0, 0, iconWidth, iconHeight),
              op = null,
              observer = component)
  }
  else {
    drawImage(g = g,
              image = image!!,
              x = start2,
              y = start,
              dw = iconWidth,
              dh = length,
              sourceBounds = Rectangle(0, 0, iconWidth, iconHeight),
              op = null,
              observer = component)
  }
}
