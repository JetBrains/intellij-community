// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.view.ViewBox
import java.awt.RenderingHints
import java.awt.image.BufferedImage

/**
 * Renders [document] to a new [BufferedImage], applying the `@2x` / `data-scaled` HiDPI
 * normalization convention: an icon file whose root `<svg>` is marked `data-scaled="true"`
 * and whose path ends in `@2x` is authored at double the target logical size, so we divide
 * [scale] by 2 to compensate.
 */
internal fun renderSvg(document: ParsedSvgDocument,
                       scale: Float,
                       path: String? = null,
                       baseWidth: Float = 16f,
                       baseHeight: Float = 16f): BufferedImage {
  val normalizingScale = if (document.isDataScaled && path?.contains("@2x") == true) 2f else 1f
  val imageScale = scale / normalizingScale

  return withSvgSize(document, baseWidth, baseHeight) { w, h ->
    renderSvgWithSize(document = document, width = w * imageScale, height = h * imageScale)
  }
}

/**
 * Invokes [consumer] with the SVG's logical (width, height), falling back to
 * ([baseWidth], [baseHeight]) for any dimension that the root `<svg>` left unspecified.
 */
internal inline fun <T> withSvgSize(document: ParsedSvgDocument, baseWidth: Float, baseHeight: Float, consumer: (Float, Float) -> T): T {
  val size = document.document.size()
  val w = if (document.rawWidth == null) baseWidth else size.width
  val h = if (document.rawHeight == null) baseHeight else size.height
  return consumer(w, h)
}

/**
 * Paints [document] into a new [width] × [height] ARGB [BufferedImage] with high-quality
 * rendering hints. The SVG's own viewBox is mapped into the requested bounds.
 */
internal fun renderSvgWithSize(document: ParsedSvgDocument, width: Float, height: Float): BufferedImage {
  @Suppress("UndesirableClassUsage")
  val result = BufferedImage((width + 0.5f).toInt(), (height + 0.5f).toInt(), BufferedImage.TYPE_INT_ARGB)
  val g = result.createGraphics()
  try {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
    document.document.render(null, g, ViewBox(width, height))
  }
  finally {
    g.dispose()
  }
  return result
}
