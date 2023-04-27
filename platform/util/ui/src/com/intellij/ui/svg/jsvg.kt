// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.geometry.size.MeasureContext
import com.github.weisj.jsvg.nodes.SVG
import java.awt.RenderingHints
import java.awt.image.BufferedImage

internal fun renderSvg(document: SVG,
                       scale: Float,
                       path: String? = null,
                       baseWidth: Float = 16f,
                       baseHeight: Float = 16f): BufferedImage {
  val normalizingScale = if (document.isDataScaled && path?.contains("@2x") == true) 2f else 1f
  val imageScale = scale / normalizingScale

  return withSvgSize(document, baseWidth, baseHeight) { w, h ->
    renderSvgWithSize(document = document, width = w * imageScale, height = h * imageScale, defaultEm = SVGFont.defaultFontSize())
  }
}

internal inline fun <T> withSvgSize(document: SVG, baseWidth: Float, baseHeight: Float, consumer: (Float, Float) -> T): T {
  val w: Float
  val h: Float
  if (document.width.isUnspecified && document.height.isUnspecified) {
    w = baseWidth
    h = baseHeight
  }
  else {
    val defaultEm = SVGFont.defaultFontSize()
    val measureContext = MeasureContext(baseWidth, baseHeight, defaultEm, SVGFont.exFromEm(defaultEm))
    w = if (document.width.isUnspecified) baseWidth else document.width.resolveWidth(measureContext)
    h = if (document.height.isUnspecified) baseHeight else document.height.resolveHeight(measureContext)
  }
  return consumer(w, h)
}

fun renderSvgWithSize(document: SVG, width: Float, height: Float, defaultEm: Float = SVGFont.defaultFontSize()): BufferedImage {
  @Suppress("UndesirableClassUsage")
  val result = BufferedImage((width + 0.5f).toInt(), (height + 0.5f).toInt(), BufferedImage.TYPE_INT_ARGB)
  val g = result.createGraphics()
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
  g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
  document.renderWithSize(width, height, defaultEm, g)
  return result
}