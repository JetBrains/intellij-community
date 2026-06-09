// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.objOrNull
import com.intellij.agent.workbench.prompt.core.string
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
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
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.math.min
import kotlin.math.roundToInt

internal object AgentPromptScreenshotChipIcon {
  private val LOG = logger<AgentPromptScreenshotChipIcon>()

  private const val SCREENSHOT_TYPE = "screenshot"
  private const val ICON_SRC_RESOLUTION = 64
  private const val ICON_TARGET_RESOLUTION = 16
  private const val ICON_INNER_RESOLUTION = 56
  private const val PREVIEW_MAX_WIDTH = 240
  private const val PREVIEW_MAX_HEIGHT = 180

  private val BORDER_COLOR = JBColor(Color(168, 173, 189), Color(255, 255, 255, 82))

  /**
   * Builds a small circular thumbnail icon from a screenshot context item payload,
   * or returns null if the item is not a screenshot or the image cannot be loaded.
   */
  fun resolve(item: AgentPromptContextItem): Icon? {
    val sourceImage = loadScreenshotImage(item) ?: return null
    return buildCircularThumbnail(sourceImage)
  }

  fun buildPreviewTooltipComponent(item: AgentPromptContextItem, labelText: @NlsSafe String): JComponent? {
    val sourceImage = loadScreenshotImage(item) ?: return null
    val previewImage = buildPreviewImage(sourceImage)
    val imageLabel = JLabel(JBImageIcon(previewImage)).apply {
      horizontalAlignment = SwingConstants.CENTER
      verticalAlignment = SwingConstants.CENTER
      accessibleContext.accessibleName = labelText
    }
    return JPanel(BorderLayout(0, JBUI.scale(6))).apply {
      isOpaque = true
      background = UIUtil.getToolTipBackground()
      border = JBUI.Borders.empty(6)
      accessibleContext.accessibleName = labelText
      add(imageLabel, BorderLayout.CENTER)
      labelText.takeIf { it.isNotBlank() }?.let { text ->
        add(JLabel(text).apply {
          foreground = UIUtil.getToolTipForeground()
          font = UIUtil.getToolTipFont()
          horizontalAlignment = SwingConstants.CENTER
        }, BorderLayout.SOUTH)
      }
    }
  }

  private fun loadScreenshotImage(item: AgentPromptContextItem): BufferedImage? {
    val payload = item.payload.objOrNull() ?: return null
    if (payload.string("type") != SCREENSHOT_TYPE) return null
    val filePath = payload.string("filePath") ?: return null
    return loadImage(Path.of(filePath))
  }

  private fun loadImage(path: Path): BufferedImage? {
    return try {
      if (!Files.exists(path)) return null
      Files.newInputStream(path).use { ImageIO.read(it) }
    }
    catch (e: Exception) {
      LOG.debug("Failed to load screenshot thumbnail: $path", e)
      null
    }
  }

  private fun buildCircularThumbnail(sourceImage: BufferedImage): Icon {
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

  private fun buildPreviewImage(sourceImage: BufferedImage): Image {
    val maxWidth = JBUI.scale(PREVIEW_MAX_WIDTH)
    val maxHeight = JBUI.scale(PREVIEW_MAX_HEIGHT)
    val scale = min(maxWidth.toDouble() / sourceImage.width, maxHeight.toDouble() / sourceImage.height).coerceAtMost(1.0)
    val previewWidth = (sourceImage.width * scale).roundToInt().coerceAtLeast(1)
    val previewHeight = (sourceImage.height * scale).roundToInt().coerceAtLeast(1)
    return ImageUtil.scaleImage(sourceImage, previewWidth, previewHeight)
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
