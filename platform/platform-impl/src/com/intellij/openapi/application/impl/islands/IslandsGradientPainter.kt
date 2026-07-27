// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ProjectGradients
import com.intellij.ide.ProjectWidgetGradientLocationService
import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.ide.ui.GradientTextureCache
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.AbstractPainter
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.impl.IdeGlassPaneEx
import com.intellij.openapi.wm.impl.customFrameDecorations.header.CustomWindowHeaderUtil
import com.intellij.toolWindow.ToolWindowLeftToolbar
import com.intellij.ui.ColorUtil
import com.intellij.ui.Gray
import com.intellij.ui.IslandsState
import com.intellij.ui.JBColor
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.paint.PaintUtil.alignIntToInt
import com.intellij.ui.paint.PaintUtil.alignTxToInt
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Component
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.Paint
import java.awt.RadialGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.util.stream.IntStream
import javax.swing.JComponent
import javax.swing.SwingUtilities
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The list of auto replaced colors. Should contain only very specific colors, don't add widely used like `Panel.background`
 */
private val islandsGradientColors = setOf(
  // Root components
  "MainWindow.background",
  "MainToolbar.background",
  "MainToolbar.inactiveBackground",
  "ToolWindow.Stripe.background",
  "StatusBar.background",
)

private val islandsToolWindowGradientColors = setOf(
  "ToolWindow.background",
  "ToolWindow.header.background",
  "ToolWindow.Header.inactiveBackground",
)

internal fun isIslandsGradientColor(paint: Paint?): Boolean {
  val colorName = (paint as? JBColor)?.name ?: return false
  if (isColorIslandGradient() && colorName in islandsToolWindowGradientColors) {
    return true
  }
  return colorName in islandsGradientColors
}

private fun isIslandsToolWindowGradientColor(paint: Paint?): Boolean {
  val colorName = (paint as? JBColor)?.name ?: return false
  return colorName in islandsToolWindowGradientColors
}

internal class IslandsGradientPainter(private val frame: IdeFrame, private val mainColor: Color, private val enabled: () -> Boolean) : AbstractPainter() {

  private val projectWindowCustomizer = ProjectWindowCustomizerService.getInstance()

  private var doPaint = true

  override fun needsRepaint(): Boolean = enabled()

  override fun executePaint(component: Component, g: Graphics2D) {
  }

  override fun executePaint(component: Component, source: Component, g: Graphics2D) {
    if (doPaint) {
      try {
        doPaint = false

        if (isIslandsGradientColor(g.paint)) {
          islandsGradientPaint(frame, mainColor, projectWindowCustomizer, component, source, g)
        }
      }
      finally {
        doPaint = true
      }
    }
  }
}

internal fun islandsGradientPaint(frame: IdeFrame, mainColor: Color, projectWindowCustomizer: ProjectWindowCustomizerService,
                                  component: Component, source: Component, g: Graphics2D) {
  if (CustomWindowHeaderUtil.isCompactHeader()) {
    return
  }
  if (component is IdeGlassPaneEx && !component.isColorfulToolbar) {
    return
  }

  val project = frame.project ?: return

  if (isColorIslandGradient()) {
    doColorGradientPaint(project, projectWindowCustomizer, frame, component, g)
  }
  else {
    doGradientPaint(frame, mainColor, project, projectWindowCustomizer, component, source, g)
  }
}

internal fun isColorIslandGradient(): Boolean = Registry.`is`("idea.islands.color.gradient.enabled", false)

/**
 * Kill switch for the dithered gradient rendering; when disabled, the previous Java2D gradient painting is used.
 */
private fun isGradientDitheringEnabled(): Boolean = Registry.`is`("ide.islands.gradient.dithering.enabled", false)

internal fun isColorIslandGradientAvailable(): Boolean = IslandsState.isEnabled() && isColorIslandGradient()

private fun doGradientPaint(frame: IdeFrame, mainColor: Color, project: Project, projectWindowCustomizer: ProjectWindowCustomizerService,
                            component: Component, source: Component, g: Graphics2D) {
  if (!isGradientDitheringEnabled()) {
    doGradientPaintLegacy(frame, mainColor, project, projectWindowCustomizer, component, source, g)
    return
  }

  val centerX = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

  val ctx = ScaleContext.create(g)

  val length = JBUI.getInt("RecentProject.MainToolbarGradient.width", 700)
  val height = JBUI.getInt("RecentProject.MainToolbarGradient.height", 200)

  val leftWidth = alignIntToInt(centerX.toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
  val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)
  val totalWidth = alignIntToInt(leftWidth + rightWidth, ctx, PaintUtil.RoundingMode.CEIL, null)

  val fullBounds = Rectangle(totalWidth, height)
  val bounds = if (SystemInfo.isLinux && source is ToolWindowLeftToolbar) fullBounds else g.clipBounds?.intersection(fullBounds) ?: fullBounds
  if (bounds.isEmpty) {
    return
  }

  val cache = getGradientCache(frame.component)
  val centerColor = projectWindowCustomizer.getGradientProjectColor(project)
  val blendedColor = cache.getBlendedColor(mainColor, centerColor)

  val gradientImage = cache.getFrameGradientImage(g, leftWidth, rightWidth, height, mainColor, blendedColor)
  val initialComposite = g.composite
  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D

  if (SwingUtilities.getWindowAncestor(frame.component)?.isActive == false) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    val componentFullBounds = Rectangle(component.width, component.height)
    val componentBounds = g.clipBounds?.intersection(componentFullBounds) ?: componentFullBounds
    if (!componentBounds.isEmpty) {
      g.color = mainColor
      g.fillRect(componentBounds.x, componentBounds.y, componentBounds.width, componentBounds.height)
    }

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  alignTxToInt(g, null, true, true, PaintUtil.RoundingMode.FLOOR)

  val imageBounds = bounds.intersection(Rectangle(leftWidth + rightWidth, height))
  if (!imageBounds.isEmpty) {
    UIUtil.drawImage(g, gradientImage, imageBounds, imageBounds, null)
  }

  g.composite = initialComposite

  islandsInactiveFrameGraphics2D?.preserveComposite = false
}

private fun doGradientPaintLegacy(frame: IdeFrame, mainColor: Color, project: Project, projectWindowCustomizer: ProjectWindowCustomizerService,
                                  component: Component, source: Component, g: Graphics2D) {
  val centerX = project.service<ProjectWidgetGradientLocationService>().gradientOffsetRelativeToRootPane

  val ctx = ScaleContext.create(g)

  val length = JBUI.getInt("RecentProject.MainToolbarGradient.width", 700)
  val height = JBUI.getInt("RecentProject.MainToolbarGradient.height", 200)

  val leftWidth = alignIntToInt(centerX.toInt(), ctx, PaintUtil.RoundingMode.CEIL, null)
  val rightWidth = alignIntToInt(length, ctx, PaintUtil.RoundingMode.CEIL, null)
  val totalWidth = alignIntToInt(leftWidth + rightWidth, ctx, PaintUtil.RoundingMode.CEIL, null)

  val fullBounds = Rectangle(totalWidth, height)
  val bounds = if (SystemInfo.isLinux && source is ToolWindowLeftToolbar) fullBounds else g.clipBounds?.intersection(fullBounds) ?: fullBounds
  if (bounds.isEmpty) {
    return
  }

  val cache = getGradientCache(frame.component)
  val centerColor = projectWindowCustomizer.getGradientProjectColor(project)
  val blendedColor = cache.getBlendedColor(mainColor, centerColor)

  val leftGradientTexture = cache.left.getHorizontalTexture(g, leftWidth, mainColor, blendedColor)
  val rightGradientTexture = cache.right.getHorizontalTexture(g, rightWidth, blendedColor, mainColor, leftWidth)
  val initialComposite = g.composite
  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D

  if (SwingUtilities.getWindowAncestor(frame.component)?.isActive == false) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    val componentFullBounds = Rectangle(component.width, component.height)
    val componentBounds = g.clipBounds?.intersection(componentFullBounds) ?: componentFullBounds
    if (!componentBounds.isEmpty) {
      g.color = mainColor
      g.fillRect(componentBounds.x, componentBounds.y, componentBounds.width, componentBounds.height)
    }

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

  alignTxToInt(g, null, true, false, PaintUtil.RoundingMode.FLOOR)

  val leftBounds = bounds.intersection(Rectangle(leftWidth, height))
  if (!leftBounds.isEmpty) {
    g.paint = leftGradientTexture
    g.fillRect(leftBounds.x, leftBounds.y, leftBounds.width, leftBounds.height)
  }

  val rightBounds = bounds.intersection(Rectangle(leftWidth, 0, rightWidth, height))
  if (!rightBounds.isEmpty) {
    g.paint = rightGradientTexture
    g.fillRect(rightBounds.x, rightBounds.y, rightBounds.width, rightBounds.height)
  }

  alignTxToInt(g, null, false, true, PaintUtil.RoundingMode.FLOOR)

  g.composite = initialComposite

  val startColor = if (SystemInfo.isMac) Gray.TRANSPARENT else ColorUtil.toAlpha(mainColor, 0)
  g.paint = GradientPaint(0f, 0f, startColor, 0f, height.toFloat(), mainColor)
  g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)

  islandsInactiveFrameGraphics2D?.preserveComposite = false
}

private class GradientCache {
  // state of the legacy painting (`ide.islands.gradient.dithering.enabled=false`)
  val left = GradientTextureCache()
  val right = GradientTextureCache()
  var legacyWidth = 0
  var legacyHeight = 0
  var legacyIndex = -1
  var legacyIsBright = false
  var legacyImage: BufferedImage? = null

  private var width = 0
  private var height = 0
  private var scale = 0f
  private var colors: IntArray? = null
  private var fractions: FloatArray? = null

  // The buffer is rendered in place whenever possible: it is reallocated only when it is too small for the frame
  // (growing in coarse steps) or when the scale changes, so interactive resizing does not allocate
  // a full-frame image on every resize event
  private var buffer: BufferedImage? = null
  private var bufferWidth = 0
  private var bufferHeight = 0

  private var mainRgb = 0
  private var centerRgb = 0
  private var blendedColor: Color? = null

  private var frameGradientKey: FrameGradientKey? = null
  private var frameGradient: BufferedImage? = null

  fun getBlendedColor(mainColor: Color, centerColor: Color): Color {
    if (blendedColor == null || mainRgb != mainColor.rgb || centerRgb != centerColor.rgb) {
      mainRgb = mainColor.rgb
      centerRgb = centerColor.rgb
      blendedColor = ColorUtil.blendColorsInRgb(mainColor, centerColor, 0.85 * (centerColor.alpha.toDouble() / 255))
    }
    return blendedColor!!
  }

  fun getFrameGradientImage(g: Graphics2D, leftWidth: Int, rightWidth: Int, height: Int, mainColor: Color, blendedColor: Color): BufferedImage {
    val key = FrameGradientKey(JBUIScale.sysScale(g), leftWidth, rightWidth, height, mainColor.rgb, blendedColor.rgb)
    val oldKey = frameGradientKey
    var result = frameGradient
    if (result != null && key == oldKey) {
      return result
    }
    if (result == null || oldKey == null || oldKey.scale != key.scale ||
        oldKey.leftWidth + oldKey.rightWidth != leftWidth + rightWidth || oldKey.height != height) {
      frameGradient = null
      result = ImageUtil.createImage(g, leftWidth + rightWidth, height, BufferedImage.TYPE_INT_ARGB)
    }
    renderFrameGradientImage(result, leftWidth, rightWidth, mainColor, blendedColor)
    frameGradient = result
    frameGradientKey = key
    return result
  }

  fun getColorGradientImage(g: Graphics2D, width: Int, height: Int, info: ProjectGradients): BufferedImage {
    val scale = JBUIScale.sysScale(g)
    val colors = intArrayOf(info.diagonalColor1.rgb, info.diagonalColor2.rgb, info.diagonalColor3.rgb, info.diagonalColor4.rgb,
                            info.radialColor1.rgb, info.radialColor2.rgb,
                            info.horizontalColor1.rgb, info.horizontalColor2.rgb,
                            info.verticalColor1.rgb, info.verticalColor2.rgb)
    val fractions = floatArrayOf(info.getDiagonalFraction1(0f), info.getDiagonalFraction2(0.13f),
                                 info.getDiagonalFraction3(0.3f), info.getDiagonalFraction4(1f))
    var result = buffer
    if (result != null && width == this.width && height == this.height && scale == this.scale &&
        colors.contentEquals(this.colors) && fractions.contentEquals(this.fractions)) {
      return result
    }

    if (result == null || scale != this.scale || bufferWidth < width || bufferHeight < height) {
      buffer = null
      bufferWidth = roundUpBufferSize(width)
      bufferHeight = roundUpBufferSize(height)
      result = ImageUtil.createImage(g, bufferWidth, bufferHeight, BufferedImage.TYPE_INT_ARGB)
    }
    renderColorGradientImage(result, width, height, scale, info)
    this.width = width
    this.height = height
    this.scale = scale
    this.colors = colors
    this.fractions = fractions
    buffer = result
    return result
  }
}

/**
 * Rounds the gradient buffer dimensions up so that enlarging a frame reallocates the buffer once per step, not on every resize event.
 */
private fun roundUpBufferSize(size: Int): Int = (size + 127) and -128

private data class FrameGradientKey(
  val scale: Float,
  val leftWidth: Int,
  val rightWidth: Int,
  val height: Int,
  val mainRgb: Int,
  val blendedRgb: Int,
)

/**
 * Renders the frame gradient into [image]: a horizontal ramp from [mainColor] to [blendedColor] at the project widget
 * and back to [mainColor], faded out vertically into [mainColor] towards the bottom.
 *
 * Java2D computes gradients and composites them in 8 bits per channel, so painting this as a stack of gradient fills
 * quantizes the result at every step and produces visible color banding on a large area between two close colors.
 * Instead, the composed color is computed per pixel in floating point and mapped to 8 bits with Floyd–Steinberg dithering,
 * like [com.intellij.ui.AppUIUtil.createHorizontalGradientTexture] does.
 */
private fun renderFrameGradientImage(image: BufferedImage, leftWidth: Int, rightWidth: Int, mainColor: Color, blendedColor: Color) {
  val raster = image.raster
  val imageWidth = raster.width
  val imageHeight = raster.height

  val horizontal = DoubleArray(imageWidth * 3)
  val leftDeviceWidth = imageWidth * leftWidth.toDouble() / (leftWidth + rightWidth)
  for (x in 0 until imageWidth) {
    val pixelCenter = x + 0.5
    val from: Color
    val to: Color
    val fraction: Double
    if (pixelCenter < leftDeviceWidth) {
      from = mainColor
      to = blendedColor
      fraction = pixelCenter / leftDeviceWidth
    }
    else {
      from = blendedColor
      to = mainColor
      fraction = ((pixelCenter - leftDeviceWidth) / (imageWidth - leftDeviceWidth)).coerceAtMost(1.0)
    }
    horizontal[x * 3] = from.red + (to.red - from.red) * fraction
    horizontal[x * 3 + 1] = from.green + (to.green - from.green) * fraction
    horizontal[x * 3 + 2] = from.blue + (to.blue - from.blue) * fraction
  }

  val main = doubleArrayOf(mainColor.red.toDouble(), mainColor.green.toDouble(), mainColor.blue.toDouble())
  val row = IntArray(imageWidth)
  var currentError = DoubleArray(imageWidth * 3)
  var nextError = DoubleArray(imageWidth * 3)

  for (y in 0 until imageHeight) {
    val alpha = (y + 0.5) / imageHeight
    for (x in 0 until imageWidth) {
      var argb = 0xFF
      for (channel in 0..2) {
        val i = x * 3 + channel
        val ideal = horizontal[i] + (main[channel] - horizontal[i]) * alpha + currentError[i]
        val quantized = ideal.roundToInt().coerceIn(0, 255)
        val error = ideal - quantized
        argb = (argb shl 8) or quantized
        if (x + 1 < imageWidth) {
          currentError[i + 3] += error * 7 / 16
          nextError[i + 3] += error * 1 / 16
        }
        if (x > 0) {
          nextError[i - 3] += error * 3 / 16
        }
        nextError[i] += error * 5 / 16
      }
      row[x] = argb
    }
    raster.setDataElements(0, y, imageWidth, 1, row)
    val errors = currentError
    currentError = nextError
    nextError = errors
    nextError.fill(0.0)
  }
}

private fun getGradientCache(root: JComponent): GradientCache {
  val key = "GradientCache"
  val gradientCache = root.getClientProperty(key)
  if (gradientCache is GradientCache) {
    return gradientCache
  }

  val newValue = GradientCache()
  root.putClientProperty(key, newValue)
  return newValue
}

private fun doColorGradientPaint(project: Project, projectWindowCustomizer: ProjectWindowCustomizerService, frame: IdeFrame,
                                 component: Component, g: Graphics2D) {
  if (!isGradientDitheringEnabled()) {
    doColorGradientPaintLegacy(project, projectWindowCustomizer, frame, component, g)
    return
  }

  val width = component.width
  val height = component.height
  val fullBounds = Rectangle(width, height)
  val bounds = g.clipBounds?.intersection(fullBounds) ?: fullBounds
  if (bounds.isEmpty) {
    return
  }

  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D
  val initialComposite = g.composite
  val info = projectWindowCustomizer.getProjectGradients(project)
  val isToolWindowColor = isIslandsToolWindowGradientColor(g.paint)

  val isActive = SwingUtilities.getWindowAncestor(component)?.isActive != false

  if (isToolWindowColor) {
    var alpha = JBUI.getFloat("Island.toolWindowAlpha", 0.2f)
    if (!isActive) {
      alpha *= islandsInactiveAlpha
    }
    islandsInactiveFrameGraphics2D?.preserveComposite = true
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
  }
  else if (!isActive) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  val cache = getGradientCache(frame.component)
  val image = cache.getColorGradientImage(g, width, height, info)

  // On macOS tool window islands show the gradient itself washed by the alpha composite;
  // on other platforms they are filled with a single color picked from the gradient
  @Suppress("UseJBColor")
  if (isToolWindowColor && !SystemInfo.isMac) {
    g.color = Color(image.getRGB(bounds.centerX.toInt(), bounds.centerY.toInt()))
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
  }
  else {
    UIUtil.drawImage(g, image, bounds, bounds, null)
  }

  g.composite = initialComposite
  islandsInactiveFrameGraphics2D?.preserveComposite = false
}

private fun doColorGradientPaintLegacy(project: Project, projectWindowCustomizer: ProjectWindowCustomizerService, frame: IdeFrame,
                                       component: Component, g: Graphics2D) {
  val width = component.width
  val height = component.height
  val fullBounds = Rectangle(width, height)
  val bounds = g.clipBounds?.intersection(fullBounds) ?: fullBounds
  if (bounds.isEmpty) {
    return
  }

  val islandsInactiveFrameGraphics2D = g as? IslandsInactiveFrameGraphics2D
  val initialComposite = g.composite
  val info = projectWindowCustomizer.getProjectGradients(project)
  val isToolWindowColor = isIslandsToolWindowGradientColor(g.paint)

  if (isToolWindowColor) {
    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, JBUI.getFloat("Island.toolWindowAlpha", 0.2f))
  }
  else if (SwingUtilities.getWindowAncestor(component)?.isActive == false) {
    islandsInactiveFrameGraphics2D?.preserveComposite = true

    g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, islandsInactiveAlpha)
  }

  val cache = getGradientCache(frame.component)

  if (SystemInfo.isMac) {
    paintColorGradientLegacy(width, height, g, info, cache)
  }
  else {
    val isBright = JBColor.isBright()

    if (cache.legacyImage == null || cache.legacyIsBright != isBright || cache.legacyWidth != width || cache.legacyHeight != height || cache.legacyIndex != info.index) {
      cache.legacyIndex = info.index
      cache.legacyWidth = width
      cache.legacyHeight = height
      cache.legacyIsBright = isBright

      val image = ImageUtil.createImage(g, width, height, BufferedImage.TYPE_INT_ARGB)
      cache.legacyImage = image
      paintColorGradientLegacy(width, height, image.createGraphics(), info, cache)
    }

    @Suppress("UseJBColor")
    if (isToolWindowColor) {
      g.color = Color(cache.legacyImage!!.getRGB(bounds.centerX.toInt(), bounds.centerY.toInt()))
      g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
    }
    else {
      UIUtil.drawImage(g, cache.legacyImage!!, bounds, bounds, null)
    }
  }

  g.composite = initialComposite
  islandsInactiveFrameGraphics2D?.preserveComposite = false
}

private fun paintColorGradientLegacy(width: Int, height: Int, g: Graphics2D, info: ProjectGradients, cache: GradientCache) {
  g.paint = LinearGradientPaint(0f, 0f, width.toFloat(), height.toFloat(),
                                floatArrayOf(info.getDiagonalFraction1(0f), info.getDiagonalFraction2(0.13f),
                                             info.getDiagonalFraction3(0.3f), info.getDiagonalFraction4(1f)),
                                arrayOf(info.diagonalColor1, info.diagonalColor2, info.diagonalColor3, info.diagonalColor4))

  g.fillRect(0, 0, width, height)

  val ovalRadius = width / 4f
  val ovalWidth = width / 2
  val ovalCenterX = width * 0.2f
  val ovalCenterY = 36f

  g.paint = RadialGradientPaint(ovalCenterX, ovalCenterY, ovalRadius,
                                floatArrayOf(0f, 1f),
                                arrayOf(info.radialColor1, info.radialColor2))

  g.fillOval((ovalCenterX - ovalRadius).toInt(), (ovalCenterY - ovalRadius).toInt(), ovalWidth, ovalWidth)

  g.paint = cache.left.getHorizontalTexture(g, width, info.horizontalColor1, info.horizontalColor2)

  g.fillRect(0, 0, width, height)

  g.paint = cache.right.getVerticalTexture(g, height, info.verticalColor1, info.verticalColor2)

  g.fillRect(0, 0, width, height)
}

/**
 * The 8x8 Bayer index matrix, used to compute per-pixel ordered dithering thresholds.
 */
private val BAYER_8X8 = intArrayOf(
  0, 32, 8, 40, 2, 34, 10, 42,
  48, 16, 56, 24, 50, 18, 58, 26,
  12, 44, 4, 36, 14, 46, 6, 38,
  60, 28, 52, 20, 62, 30, 54, 22,
  3, 35, 11, 43, 1, 33, 9, 41,
  51, 19, 59, 27, 49, 17, 57, 25,
  15, 47, 7, 39, 13, 45, 5, 37,
  63, 31, 55, 23, 61, 29, 53, 21,
)

/**
 * The number of image rows rendered by one parallel task of [renderColorGradientImage].
 */
private const val GRADIENT_BAND_HEIGHT = 64

/**
 * Renders the whole-frame project gradient (a diagonal 4-stop gradient, a radial spot at the project widget
 * and horizontal/vertical washes on top) of the given user-space [width] x [height] into [image],
 * which may be larger than the gradient area (see [GradientCache.getColorGradientImage]).
 *
 * Java2D computes gradients and composites them in 8 bits per channel, so painting this as a stack of gradient fills
 * quantizes the result at every step and produces visible color banding.
 * Instead, the composed color is computed per pixel in floating point and quantized once using ordered (Bayer) dithering.
 * Unlike error diffusion, ordered dithering keeps every pixel independent, so rows are rendered in parallel:
 * the image is re-rendered on every frame resize and must not take more than a few milliseconds.
 *
 * The image is composed at full opacity; the inactive frame and tool window alphas are applied when it is drawn.
 * The legacy macOS painting applied them to each gradient layer instead, with a slightly different result;
 * the composed variant matches what other platforms have always shown.
 */
@Suppress("UseJBColor")
private fun renderColorGradientImage(image: BufferedImage, width: Int, height: Int, scale: Float, info: ProjectGradients) {
  val raster = image.raster
  val gradientWidth = width * scale.toDouble()
  val gradientHeight = height * scale.toDouble()
  // one extra device pixel, so that the edge of the blitted region never samples unrendered pixels of a larger buffer
  val deviceWidth = min(raster.width, ceil(gradientWidth).toInt() + 1)
  val deviceHeight = min(raster.height, ceil(gradientHeight).toInt() + 1)

  val diagonalFractions = doubleArrayOf(info.getDiagonalFraction1(0f).toDouble(), info.getDiagonalFraction2(0.13f).toDouble(),
                                        info.getDiagonalFraction3(0.3f).toDouble(), info.getDiagonalFraction4(1f).toDouble())
  // snapshot JBColors: their channel accessors resolve the current LaF color on every call, which is too slow per pixel
  val diagonalColors = arrayOf(Color(info.diagonalColor1.rgb, true), Color(info.diagonalColor2.rgb, true),
                               Color(info.diagonalColor3.rgb, true), Color(info.diagonalColor4.rgb, true))

  val radialColor1 = Color(info.radialColor1.rgb, true)
  val radialColor2 = Color(info.radialColor2.rgb, true)
  val radialCenterX = gradientWidth * 0.2
  val radialCenterY = 36.0 * scale
  val radialRadius = gradientWidth / 4.0

  val horizontal = premultipliedRamp(info.horizontalColor1, info.horizontalColor2, deviceWidth, gradientWidth)
  val vertical = premultipliedRamp(info.verticalColor1, info.verticalColor2, deviceHeight, gradientHeight)

  // the diagonal gradient fraction is a linear function of the pixel position
  val diagonalNorm = gradientWidth * gradientWidth + gradientHeight * gradientHeight
  val diagonalStepX = gradientWidth / diagonalNorm

  IntStream.range(0, (deviceHeight + GRADIENT_BAND_HEIGHT - 1) / GRADIENT_BAND_HEIGHT).parallel().forEach { band ->
    val row = IntArray(deviceWidth)
    for (y in band * GRADIENT_BAND_HEIGHT until min((band + 1) * GRADIENT_BAND_HEIGHT, deviceHeight)) {
      renderColorGradientRow(y, row, gradientHeight, diagonalNorm, diagonalStepX, diagonalFractions, diagonalColors,
                             radialCenterX, radialCenterY, radialRadius, radialColor1, radialColor2, horizontal, vertical)
      raster.setDataElements(0, y, deviceWidth, 1, row)
    }
  }
}

private fun renderColorGradientRow(y: Int, row: IntArray, gradientHeight: Double,
                                   diagonalNorm: Double, diagonalStepX: Double, diagonalFractions: DoubleArray, diagonalColors: Array<Color>,
                                   radialCenterX: Double, radialCenterY: Double, radialRadius: Double, radialColor1: Color, radialColor2: Color,
                                   horizontal: DoubleArray, vertical: DoubleArray) {
  val pixelCenterY = y + 0.5
  val diagonalRowBase = pixelCenterY * gradientHeight / diagonalNorm + 0.5 * diagonalStepX
  val radialDy = pixelCenterY - radialCenterY
  val radialDySquared = radialDy * radialDy
  val verticalOffset = y * 4
  val bayerRowOffset = (y and 7) shl 3

  for (x in row.indices) {
    // the diagonal gradient defines the base, it is expected to be opaque
    val t = diagonalRowBase + x * diagonalStepX
    var segment = 2
    while (segment > 0 && t < diagonalFractions[segment]) segment--
    val span = diagonalFractions[segment + 1] - diagonalFractions[segment]
    val fraction = if (span > 0) ((t - diagonalFractions[segment]) / span).coerceIn(0.0, 1.0) else 0.0
    val from = diagonalColors[segment]
    val to = diagonalColors[segment + 1]
    var alpha = (from.alpha + (to.alpha - from.alpha) * fraction) / 255.0
    var red = (from.red + (to.red - from.red) * fraction) * alpha
    var green = (from.green + (to.green - from.green) * fraction) * alpha
    var blue = (from.blue + (to.blue - from.blue) * fraction) * alpha

    val radialDx = x + 0.5 - radialCenterX
    val distanceSquared = radialDx * radialDx + radialDySquared
    if (distanceSquared < radialRadius * radialRadius) {
      val radialFraction = sqrt(distanceSquared) / radialRadius
      val srcAlpha = (radialColor1.alpha + (radialColor2.alpha - radialColor1.alpha) * radialFraction) / 255.0
      val rest = 1 - srcAlpha
      red = (radialColor1.red + (radialColor2.red - radialColor1.red) * radialFraction) * srcAlpha + red * rest
      green = (radialColor1.green + (radialColor2.green - radialColor1.green) * radialFraction) * srcAlpha + green * rest
      blue = (radialColor1.blue + (radialColor2.blue - radialColor1.blue) * radialFraction) * srcAlpha + blue * rest
      alpha = srcAlpha + alpha * rest
    }

    val horizontalOffset = x * 4
    var rest = 1 - horizontal[horizontalOffset + 3]
    red = horizontal[horizontalOffset] + red * rest
    green = horizontal[horizontalOffset + 1] + green * rest
    blue = horizontal[horizontalOffset + 2] + blue * rest
    alpha = horizontal[horizontalOffset + 3] + alpha * rest

    rest = 1 - vertical[verticalOffset + 3]
    red = vertical[verticalOffset] + red * rest
    green = vertical[verticalOffset + 1] + green * rest
    blue = vertical[verticalOffset + 2] + blue * rest
    alpha = vertical[verticalOffset + 3] + alpha * rest

    val threshold = (BAYER_8X8[bayerRowOffset or (x and 7)] + 0.5) / 64.0
    row[x] = if (alpha <= 0.0) 0
    else {
      val inverse = 1 / alpha
      val a8 = (alpha * 255 + threshold).toInt().coerceIn(0, 255)
      val r8 = (red * inverse + threshold).toInt().coerceIn(0, 255)
      val g8 = (green * inverse + threshold).toInt().coerceIn(0, 255)
      val b8 = (blue * inverse + threshold).toInt().coerceIn(0, 255)
      (a8 shl 24) or (r8 shl 16) or (g8 shl 8) or b8
    }
  }
}

/**
 * Interpolates [fromColor] to [toColor] over [extent] device pixels in non-premultiplied sRGB (as Java2D gradients do)
 * and returns 4 premultiplied doubles for each of the [size] pixels:
 * red, green, blue in the 0..255 range multiplied by alpha, and alpha in 0..1.
 */
@Suppress("UseJBColor")
private fun premultipliedRamp(fromColor: Color, toColor: Color, size: Int, extent: Double): DoubleArray {
  val from = Color(fromColor.rgb, true)
  val to = Color(toColor.rgb, true)
  val result = DoubleArray(size * 4)
  for (i in 0 until size) {
    val fraction = ((i + 0.5) / extent).coerceAtMost(1.0)
    val alpha = (from.alpha + (to.alpha - from.alpha) * fraction) / 255.0
    result[i * 4] = (from.red + (to.red - from.red) * fraction) * alpha
    result[i * 4 + 1] = (from.green + (to.green - from.green) * fraction) * alpha
    result[i * 4 + 2] = (from.blue + (to.blue - from.blue) * fraction) * alpha
    result[i * 4 + 3] = alpha
  }
  return result
}
