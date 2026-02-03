// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.impl

import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JreHiDpiUtil.isJreHiDPIEnabled
import com.intellij.ui.scale.JBUIScale.sysScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.util.IconUtil.cropIcon
import com.intellij.util.ui.ImageUtil
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.Icon
import javax.swing.JComponent
import kotlin.math.ceil
import kotlin.math.floor

/**
 * @author Konstantin Bulenkov
 */
internal class ShadowPainter(private val top: Icon,
                    private val topRight: Icon,
                    private val right: Icon,
                    private val bottomRight: Icon,
                    private val bottom: Icon,
                    private val bottomLeft: Icon,
                    private val left: Icon,
                    private val topLeft: Icon) {
  private var croppedTop: Icon? = null
  private var croppedRight: Icon? = null
  private var croppedBottom: Icon? = null
  private var croppedLeft: Icon? = null
  private var borderColor: Color? = null

  init {
    updateIcons(null)
    ApplicationManager.getApplication().messageBus.connect().subscribe(LafManagerListener.TOPIC, LafManagerListener { updateIcons(null) })
  }

  private var cachedScaleContext: ScaleContext? = null

  fun setBorderColor(borderColor: Color?) {
    this.borderColor = borderColor
  }

  fun createShadow(c: JComponent, width: Int, height: Int): BufferedImage {
    val image = c.graphicsConfiguration.createCompatibleImage(width, height, Transparency.TRANSLUCENT)
    val g = image.createGraphics()
    paintShadow(c, g, 0, 0, width, height)
    g.dispose()
    return image
  }

  private fun updateIcons(scaleContext: ScaleContext?) {
    updateIcon(top, scaleContext) { croppedTop = cropIcon(top, 1, Int.MAX_VALUE) }
    updateIcon(topRight, scaleContext) {}
    updateIcon(right, scaleContext) { croppedRight = cropIcon(right, Int.MAX_VALUE, 1) }
    updateIcon(bottomRight, scaleContext) {}
    updateIcon(bottom, scaleContext) { croppedBottom = cropIcon(bottom, 1, Int.MAX_VALUE) }
    updateIcon(bottomLeft, scaleContext) {}
    updateIcon(left, scaleContext) { croppedLeft = cropIcon(left, Int.MAX_VALUE, 1) }
    updateIcon(topLeft, scaleContext) {}
  }

  @Suppress("GraphicsSetClipInspection")
  fun paintShadow(c: Component?, g: Graphics2D, x: Int, y: Int, width: Int, height: Int) {
    val newScaleContext = ScaleContext.create(g)
    if (cachedScaleContext == null) {
      cachedScaleContext = newScaleContext
      updateIcons(null)
    }
    else if (cachedScaleContext!!.update(newScaleContext)) {
      updateIcons(cachedScaleContext)
    }

    val leftSize = croppedLeft!!.iconWidth
    val rightSize = croppedRight!!.iconWidth
    val bottomSize = croppedBottom!!.iconHeight
    val topSize = croppedTop!!.iconHeight
    val delta = topLeft.iconHeight + bottomLeft.iconHeight - height
    // Corner icons are overlapping. Need to handle this.
    if (delta > 0) {
      val clip = g.clip
      val topHeight = topLeft.iconHeight - delta / 2
      val top = Area(Rectangle2D.Float(x.toFloat(), y.toFloat(), width.toFloat(), topHeight.toFloat()))
      if (clip != null) {
        top.intersect(Area(clip))
      }
      g.clip = top
      topLeft.paintIcon(c, g, x, y)
      topRight.paintIcon(c, g, x + width - topRight.iconWidth, y)
      val bottomHeight = bottomLeft.iconHeight - delta + delta / 2
      val bottom = Area(Rectangle2D.Float(x.toFloat(), (y + topHeight).toFloat(), width.toFloat(), bottomHeight.toFloat()))
      if (clip != null) {
        bottom.intersect(Area(clip))
      }
      g.clip = bottom
      bottomLeft.paintIcon(c, g, x, y + height - bottomLeft.iconHeight)
      bottomRight.paintIcon(c, g, x + width - bottomRight.iconWidth, y + height - bottomRight.iconHeight)
      g.clip = clip
    }
    else {
      topLeft.paintIcon(c, g, x, y)
      topRight.paintIcon(c, g, x + width - topRight.iconWidth, y)
      bottomLeft.paintIcon(c, g, x, y + height - bottomLeft.iconHeight)
      bottomRight.paintIcon(c, g, x + width - bottomRight.iconWidth, y + height - bottomRight.iconHeight)
    }
    fill(g = g,
         pattern = croppedTop!!,
         x = x,
         y = y,
         from = topLeft.iconWidth,
         to = width - topRight.iconWidth,
         horizontally = true)
    fill(g = g,
         pattern = croppedBottom!!,
         x = x,
         y = y + height - bottomSize,
         from = bottomLeft.iconWidth,
         to = width - bottomRight.iconWidth,
         horizontally = true)
    fill(g = g,
         pattern = croppedLeft!!,
         x = x,
         y = y,
         from = topLeft.iconHeight,
         to = height - bottomLeft.iconHeight,
         horizontally = false)
    fill(g = g,
         pattern = croppedRight!!,
         x = x + width - rightSize,
         y = y,
         from = topRight.iconHeight,
         to = height - bottomRight.iconHeight,
         horizontally = false)
    if (borderColor != null) {
      g.color = borderColor
      g.drawRect(x + leftSize - 1, y + topSize - 1, width - leftSize - rightSize + 1, height - topSize - bottomSize + 1)
    }
  }
}

private inline fun updateIcon(icon: Icon, scaleContext: ScaleContext?, r: () -> Unit) {
  if (icon is ScaleContextAware) {
    icon.updateScaleContext(scaleContext)
  }
  r()
}

private fun fill(g: Graphics, pattern: Icon, x: Int, y: Int, from: Int, to: Int, horizontally: Boolean) {
  val scale = sysScale(g as Graphics2D).toDouble()
  if (isJreHiDPIEnabled() && ceil(scale) > scale) {
    // direct painting for a fractional scale
    val image = ImageUtil.toBufferedImage(IconLoader.toImage(icon = pattern) ?: BufferedImage(1, 0, BufferedImage.TYPE_INT_ARGB))
    val patternSize = if (horizontally) image.width else image.height
    val g2d = g.create() as Graphics2D
    try {
      g2d.scale(1 / scale, 1 / scale)
      g2d.translate(x * scale, y * scale)
      var at = floor(from * scale).toInt()
      while (at < to * scale) {
        if (horizontally) {
          g2d.drawImage(image, at, 0, null)
        }
        else {
          g2d.drawImage(image, 0, at, null)
        }
        at += patternSize
      }
    }
    finally {
      g2d.dispose()
    }
  }
  else {
    for (at in from until to) {
      if (horizontally) {
        pattern.paintIcon(null, g, x + at, y)
      }
      else {
        pattern.paintIcon(null, g, x, y + at)
      }
    }
  }
}
