// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import java.awt.Color
import java.awt.Graphics2D
import java.awt.Image
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.swing.Icon

internal object AgentPromptScreenshotChipIcon {
  private val LOG = logger<AgentPromptScreenshotChipIcon>()

  private const val SCREENSHOT_TYPE = "screenshot"
  private const val ICON_SRC_RESOLUTION = 64
  private const val ICON_TARGET_RESOLUTION = 16
  private const val ICON_INNER_RESOLUTION = 56

  private val BORDER_COLOR = JBColor(Color(168, 173, 189), Color(255, 255, 255, 82))

  /**
   * Builds a small circular thumbnail icon from a screenshot context item payload,
   * or returns null if the item is not a screenshot or the image cannot be loaded.
   */
  fun resolve(item: AgentPromptContextItem): Icon? {
    val payload = item.payload.objOrNull() ?: return null
    if (payload.string("type") != SCREENSHOT_TYPE) return null
    val filePath = payload.string("filePath") ?: return null
    return buildCircularThumbnail(Path.of(filePath))
  }

  private fun buildCircularThumbnail(path: Path): Icon? {
    val sourceImage = try {
      if (!Files.exists(path)) return null
      Files.newInputStream(path).use { ImageIO.read(it) }
    }
    catch (e: Exception) {
      LOG.debug("Failed to load screenshot thumbnail: $path", e)
      return null
    } ?: return null

    val canvas = ImageUtil.createImage(ICON_SRC_RESOLUTION, ICON_SRC_RESOLUTION, BufferedImage.TYPE_INT_ARGB)
    val g = canvas.createGraphics() as Graphics2D
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
      g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY)

      // Outer circle — border ring
      g.color = BORDER_COLOR
      g.clip(Ellipse2D.Float(0f, 0f, ICON_SRC_RESOLUTION.toFloat(), ICON_SRC_RESOLUTION.toFloat()))
      g.fillRect(0, 0, ICON_SRC_RESOLUTION, ICON_SRC_RESOLUTION)

      // Inner circle — image area
      val inset = (ICON_SRC_RESOLUTION - ICON_INNER_RESOLUTION) / 2f
      g.clip(Ellipse2D.Float(inset, inset, ICON_INNER_RESOLUTION.toFloat(), ICON_INNER_RESOLUTION.toFloat()))

      // Cover scaling: shorter side fills the inner circle, center-crop the rest
      val srcW = sourceImage.width
      val srcH = sourceImage.height
      val fillSize = ICON_INNER_RESOLUTION.toFloat()
      val scale: Float
      val drawW: Int
      val drawH: Int
      if (srcW < srcH) {
        scale = fillSize / srcW
        drawW = fillSize.toInt()
        drawH = (srcH * scale).toInt()
      }
      else if (srcH < srcW) {
        scale = fillSize / srcH
        drawW = (srcW * scale).toInt()
        drawH = fillSize.toInt()
      }
      else {
        drawW = fillSize.toInt()
        drawH = fillSize.toInt()
      }
      val drawX = (ICON_SRC_RESOLUTION - drawW) / 2
      val drawY = (ICON_SRC_RESOLUTION - drawH) / 2
      g.drawImage(sourceImage, drawX, drawY, drawW, drawH, null)
    }
    finally {
      g.dispose()
    }

    return HiDpiCircleIcon(canvas, ICON_TARGET_RESOLUTION)
  }

  /** JBImageIcon for proper HiDPI painting. */
  private class HiDpiCircleIcon(srcImage: BufferedImage, private val size: Int) : JBImageIcon(srcImage) {
    private var cachedImageData: Pair<Image, Int>? = null

    override fun getImage(): Image {
      val scaledSize = JBUIScale.scale(size)
      val (cachedImage, cachedSize) = cachedImageData ?: (null to 0)
      if (cachedSize == scaledSize && cachedImage != null) {
        return cachedImage
      }
      val scaledImage = ImageUtil.scaleImage(super.getImage(), iconWidth, iconHeight)
      cachedImageData = scaledImage to scaledSize
      return scaledImage
    }

    override fun getIconWidth(): Int = JBUIScale.scale(size)
    override fun getIconHeight(): Int = JBUIScale.scale(size)
  }
}
