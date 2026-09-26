// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
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
 * Invokes [consumer] with the SVG's intrinsic logical (width, height) — under IDEA's icon
 * convention, not the SVG sizing spec.
 *
 * The convention is older than this code: every IDEA icon is treated as ([baseWidth], [baseHeight])
 * unless its root `<svg>` declares an explicit `width` / `height`. The icon-class generator
 * relies on this to label every icon `16x16` regardless of how the source was authored, so we
 * do *not* fall back to the `viewBox` for absent or relative dimensions — that would
 * "discover" the SVG's true dimensions (e.g. `963×961` for an icon with `viewBox="0 0 963 961"`
 * and no width/height) and break the icon's logical 16×16 footprint everywhere downstream.
 *
 * The rules:
 *  - Absolute width/height (`px`, `em`, `ex`, unitless) → use the resolved value.
 *  - Relative width/height (`%`) → resolve against ([baseWidth], [baseHeight]) as the viewport.
 *  - Absent → fall back to ([baseWidth], [baseHeight]).
 */
internal inline fun <T> withSvgSize(document: ParsedSvgDocument, baseWidth: Float, baseHeight: Float, consumer: (Float, Float) -> T): T {
  val (w, h) = computeIntrinsicSize(document.rawWidth, document.rawHeight, baseWidth, baseHeight)
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

/**
 * See [withSvgSize] for the resolution rules. Takes raw width/height strings rather than
 * [ParsedSvgDocument] so [withSvgSize] (which is `inline`) can call it without leaking the
 * private [ParsedSvgDocument] class through this function's bytecode-public signature.
 */
@PublishedApi
internal fun computeIntrinsicSize(rawWidth: String?, rawHeight: String?, baseWidth: Float, baseHeight: Float): Pair<Float, Float> {
  val em = SVGFont.defaultFontSize()
  val ex = SVGFont.exFromEm(em)
  val width = parseLengthAttribute(rawWidth)?.toPixels(baseWidth, em, ex) ?: baseWidth
  val height = parseLengthAttribute(rawHeight)?.toPixels(baseHeight, em, ex) ?: baseHeight
  return width to height
}

/**
 * Resolves the parsed dimension to a pixel value. Percentages are interpreted as fractions of
 * [viewport] — IDEA's icon convention treats the caller's [viewport] as the implicit parent,
 * not the SVG's viewBox; see [withSvgSize].
 */
private fun ImageDimension.toPixels(viewport: Float, em: Float, ex: Float): Float = when (unit) {
  ImageDimension.Unit.PX -> value
  ImageDimension.Unit.EM -> value * em
  ImageDimension.Unit.EX -> value * ex
  ImageDimension.Unit.PERCENTAGE -> value / 100f * viewport
}
