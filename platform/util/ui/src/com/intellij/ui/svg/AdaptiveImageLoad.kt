// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.geometry.size.Length
import com.github.weisj.jsvg.geometry.size.MeasureContext
import com.github.weisj.jsvg.geometry.size.Unit
import com.github.weisj.jsvg.nodes.SVG
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
  val svg = createJSvgDocument(data)
  val dimensions = getSvgDimensions(svg)
  val estimatedMemoryConsumption = data.size.toLong() * ESTIMATED_SVG_MEMORY_PER_SRC_BYTE
  return LoadedSVGImage(src, svg, dimensions, estimatedMemoryConsumption)
}

private fun getSvgDimensions(svg: SVG): ImageDimensions {
  val viewBox = SvgViewBox(svg)
  val aspectRatio = if (viewBox.height > 0f) {
    viewBox.width / viewBox.height
  }
  else 0f
  val width: ImageDimension
  val height: ImageDimension

  if (svg.width.isSpecified && svg.height.isSpecified) {
    width = svgLengthToImageDimension(svg.width)
    height = svgLengthToImageDimension(svg.height)
  }
  else if (svg.width.isSpecified) {
    width = svgLengthToImageDimension(svg.width)
    height = if (aspectRatio > 0f) {
      ImageDimension(width.unit, width.value / aspectRatio)
    }
    else {
      ImageDimension(IDUnit.PX, DEFAULT_SVG_HEIGHT)
    }
  }
  else if (svg.height.isSpecified) {
    height = svgLengthToImageDimension(svg.height)
    width = if (aspectRatio > 0f) {
      ImageDimension(height.unit, height.value * aspectRatio)
    }
    else {
      ImageDimension(IDUnit.PX, DEFAULT_SVG_WIDTH)
    }
  }
  else {
    if (viewBox.width > 0 && viewBox.height > 0) {
      width = ImageDimension(IDUnit.PX, viewBox.width)
      height = ImageDimension(IDUnit.PX, viewBox.height)
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
  } else {
    fallbackWidth = viewBox.width.takeIf { it > 0 } ?: DEFAULT_SVG_WIDTH
    fallbackHeight = viewBox.width.takeIf { it > 0} ?: DEFAULT_SVG_HEIGHT
  }

  return ImageDimensions(width, height, FloatDimensions(fallbackWidth, fallbackHeight))
}

private fun svgLengthToImageDimension(svgLength: Length): ImageDimension {
  when (svgLength.unit()) {
    Unit.PX, Unit.Raw -> return ImageDimension(IDUnit.PX, svgLength.raw())
    Unit.EM -> return ImageDimension(IDUnit.EM, svgLength.raw())
    Unit.EX -> return ImageDimension(IDUnit.EX, svgLength.raw())
    Unit.PERCENTAGE -> return ImageDimension(IDUnit.PERCENTAGE, svgLength.raw())
    else -> {
      val measureContext = MeasureContext(16f, 16f, 10f, 10f)
      return ImageDimension(IDUnit.PX, svgLength.resolveLength(measureContext))
    }
  }
}

@ApiStatus.Internal
fun rasterizeSVGImage(config: SVGRasterizationConfig): RasterizedVectorImage {
  val img = HiDPIImage(config.scale.toDouble(), config.logicalWidth.toDouble(), config.logicalHeight.toDouble(), BufferedImage.TYPE_INT_ARGB, PaintUtil.RoundingMode.FLOOR)
  val g = img.createUnscaledGraphics()
  val defaultEm = SVGFont.defaultFontSize()
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
  g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
  config.svgImage.svgNode.renderWithSize(img.width.toFloat(), img.height.toFloat(), defaultEm, g)
  return RasterizedVectorImage(img)

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
