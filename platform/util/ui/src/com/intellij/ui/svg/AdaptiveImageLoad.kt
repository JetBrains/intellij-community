// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.view.ViewBox
import com.intellij.ui.icons.HiDPIImage
import com.intellij.ui.icons.loadRasterImage
import com.intellij.ui.paint.PaintUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import com.intellij.ui.svg.ImageDimension.Unit as IDUnit

@ApiStatus.Internal
class DataWithMimeType(val data: ByteArray, val contentType: String)

//TODO use svg content bounds if both width/height and viewBox are not specified
private const val DEFAULT_SVG_WIDTH = 40f
private const val DEFAULT_SVG_HEIGHT = 40f

private const val ESTIMATED_SVG_MEMORY_PER_SRC_BYTE = 48L

internal fun loadSvg(data: ByteArray, src: String): LoadedSVGImage {
  val parsed = createJSvgDocument(data)
  val dimensions = computeImageDimensions(parsed)
  val estimatedMemoryConsumption = data.size.toLong() * ESTIMATED_SVG_MEMORY_PER_SRC_BYTE
  return LoadedSVGImage(src, parsed, dimensions, estimatedMemoryConsumption)
}

/**
 * Derives the adaptive-image [ImageDimensions] from the parsed SVG root attributes.
 *
 * Historically IDEA reached into jsvg's private `Length` fields on the `SVG` node to keep
 * the original unit (px/em/ex/%). In jsvg 2.x those fields are no longer accessible, so we
 * parse the raw attribute strings captured during [createJSvgDocument].
 */
private fun computeImageDimensions(parsed: ParsedSvgDocument): ImageDimensions {
  val viewBoxWidth: Float
  val viewBoxHeight: Float
  if (parsed.rawViewBox != null) {
    // The document's viewBox() returns the attribute value (or a synthesized box based on the
    // resolved size) — when an explicit viewBox exists, the two coincide.
    val viewBox = parsed.document.viewBox()
    viewBoxWidth = viewBox.width
    viewBoxHeight = viewBox.height
  }
  else {
    viewBoxWidth = -1f
    viewBoxHeight = -1f
  }
  val aspectRatio = if (viewBoxHeight > 0f) viewBoxWidth / viewBoxHeight else 0f

  val widthAttribute = parseLengthAttribute(parsed.rawWidth)
  val heightAttribute = parseLengthAttribute(parsed.rawHeight)

  val width: ImageDimension
  val height: ImageDimension

  if (widthAttribute != null && heightAttribute != null) {
    width = widthAttribute
    height = heightAttribute
  }
  else if (widthAttribute != null) {
    width = widthAttribute
    height = if (aspectRatio > 0f) {
      ImageDimension(width.unit, width.value / aspectRatio)
    }
    else {
      ImageDimension(IDUnit.PX, DEFAULT_SVG_HEIGHT)
    }
  }
  else if (heightAttribute != null) {
    height = heightAttribute
    width = if (aspectRatio > 0f) {
      ImageDimension(height.unit, height.value * aspectRatio)
    }
    else {
      ImageDimension(IDUnit.PX, DEFAULT_SVG_WIDTH)
    }
  }
  else {
    if (viewBoxWidth > 0 && viewBoxHeight > 0) {
      width = ImageDimension(IDUnit.PX, viewBoxWidth)
      height = ImageDimension(IDUnit.PX, viewBoxHeight)
    }
    else {
      width = ImageDimension(IDUnit.PX, DEFAULT_SVG_WIDTH)
      height = ImageDimension(IDUnit.PX, DEFAULT_SVG_HEIGHT)
    }
  }

  val fallbackWidth: Float
  val fallbackHeight: Float
  if (width.unit == IDUnit.PX && height.unit == IDUnit.PX) {
    fallbackWidth = width.value
    fallbackHeight = height.value
  }
  else {
    fallbackWidth = viewBoxWidth.takeIf { it > 0 } ?: DEFAULT_SVG_WIDTH
    fallbackHeight = viewBoxWidth.takeIf { it > 0 } ?: DEFAULT_SVG_HEIGHT
  }

  return ImageDimensions(width, height, FloatDimensions(fallbackWidth, fallbackHeight))
}

/**
 * Parses a CSS/SVG length attribute (e.g. `"16"`, `"16px"`, `"1em"`, `"50%"`) into the IDEA
 * [ImageDimension] used by the SVG pipeline. Returns `null` for absent or unparseable values.
 *
 * Supports only the units IDEA carries forward: PX, EM, EX, PERCENTAGE. Other units
 * (`pt`, `cm`, `mm`, `in`, `pc`, `Q`, `rem`, `ch`, viewport units) fall through to PX
 * using the numeric portion, matching the historical "resolve against 16×16 viewport"
 * approximation that legacy IDEA code applied for unsupported units.
 *
 * Shared with `jsvg.kt`'s renderer-side intrinsic-size computation.
 */
internal fun parseLengthAttribute(raw: String?): ImageDimension? {
  if (raw == null) return null
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return null

  fun numeric(suffixLength: Int): Float? = trimmed.substring(0, trimmed.length - suffixLength).toFloatOrNull()
  return when {
    trimmed.endsWith("%") -> numeric(1)?.let { ImageDimension(IDUnit.PERCENTAGE, it) }
    trimmed.endsWith("em", ignoreCase = true) -> numeric(2)?.let { ImageDimension(IDUnit.EM, it) }
    trimmed.endsWith("ex", ignoreCase = true) -> numeric(2)?.let { ImageDimension(IDUnit.EX, it) }
    trimmed.endsWith("px", ignoreCase = true) -> numeric(2)?.let { ImageDimension(IDUnit.PX, it) }
    else -> trimmed.toFloatOrNull()?.let { ImageDimension(IDUnit.PX, it) }
  }
}

@ApiStatus.Internal
fun rasterizeSVGImage(config: SVGRasterizationConfig): RasterizedVectorImage {
  val image = HiDPIImage(
    config.scale.toDouble(),
    config.logicalWidth.toDouble(),
    config.logicalHeight.toDouble(),
    BufferedImage.TYPE_INT_ARGB,
    PaintUtil.RoundingMode.FLOOR,
  )
  val g = image.createUnscaledGraphics()
  try {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    config.svgImage.parsed.document.render(null, g, ViewBox(image.width.toFloat(), image.height.toFloat()))
  }
  finally {
    g.dispose()
  }
  return RasterizedVectorImage(image)
}

@ApiStatus.Internal
fun loadAdaptiveImage(dataWithMimeType: DataWithMimeType, src: String): LoadedAdaptiveImage {
  when (dataWithMimeType.contentType) {
    "image/svg+xml" -> return loadSvg(dataWithMimeType.data, src)
    else -> {
      val rasterImage = loadRasterImage(ByteArrayInputStream(dataWithMimeType.data))
      val rasterWidth = rasterImage.width.toFloat()
      val rasterHeight = rasterImage.height.toFloat()
      val width = ImageDimension(IDUnit.PX, rasterWidth)
      val height = ImageDimension(IDUnit.PX, rasterHeight)
      val memorySize = rasterImage.width.toLong() * rasterImage.height * 4
      val dimensions = ImageDimensions(width, height, FloatDimensions(rasterWidth, rasterHeight))
      return LoadedRasterImage(rasterImage, dimensions, memorySize)
    }
  }
}
