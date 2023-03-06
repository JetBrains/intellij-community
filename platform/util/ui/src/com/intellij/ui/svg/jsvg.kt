// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.geometry.size.FloatSize
import com.github.weisj.jsvg.geometry.size.MeasureContext
import com.github.weisj.jsvg.nodes.SVG
import com.github.weisj.jsvg.renderer.NodeRenderer
import com.github.weisj.jsvg.renderer.RenderContext
import com.intellij.util.ImageLoader
import java.awt.RenderingHints
import java.awt.image.BufferedImage

fun renderUsingJSvg(data: String, scale: Float, path: String? = null): BufferedImage {
  return renderUsingJSvg(document = createJSvgDocument(data.encodeToByteArray()), scale = scale, path = path)
}

@Suppress("DuplicatedCode")
fun getDocumentSizeUsingJSvg(svg: SVG, scale: Float): ImageLoader.Dimension2DDouble {
  val defaultEm = SVGFont.defaultFontSize()
  val topLevelContext = MeasureContext.createInitial(FloatSize(16f, 16f), defaultEm, SVGFont.exFromEm(defaultEm))
  val w = svg.width.orElseIfUnspecified(16f).resolveWidth(topLevelContext) * scale
  val h = svg.height.orElseIfUnspecified(16f).resolveHeight(topLevelContext) * scale
  return ImageLoader.Dimension2DDouble(w.toDouble(), h.toDouble())
}

fun renderUsingJSvg(document: SVG, scale: Float, path: String? = null, baseWidth: Float = 16f, baseHeight: Float = 16f): BufferedImage {
  var normalizingScale = 1f
  if (document.isDataScaled && path?.contains("@2x") == true) {
    normalizingScale = 2f
  }
  val imageScale = scale / normalizingScale

  @Suppress("DuplicatedCode")
  val defaultEm = SVGFont.defaultFontSize()
  val topLevelContext = MeasureContext.createInitial(FloatSize(baseWidth, baseHeight), defaultEm, SVGFont.exFromEm(defaultEm))
  val w = document.width.orElseIfUnspecified(baseWidth).resolveWidth(topLevelContext) * imageScale
  val h = document.height.orElseIfUnspecified(baseHeight).resolveHeight(topLevelContext) * imageScale

  @Suppress("UndesirableClassUsage") val result = BufferedImage(w.toInt(), h.toInt(), BufferedImage.TYPE_INT_ARGB)
  val g = result.createGraphics()
  g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
  g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
  g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

  val context = RenderContext.createInitial(null, MeasureContext.createInitial(FloatSize(baseWidth * imageScale, baseHeight * imageScale),
                                                                               defaultEm,
                                                                               SVGFont.exFromEm(defaultEm)))
  val bounds = ViewBox(w, h)
  document.applyTransform(g, context)
  g.clip(bounds)
  NodeRenderer.createRenderInfo(document, context, g, null).use { info ->
    document.renderWithSize(bounds.size(), document.viewBox, info!!.context, info.g)
  }
  return result
}
