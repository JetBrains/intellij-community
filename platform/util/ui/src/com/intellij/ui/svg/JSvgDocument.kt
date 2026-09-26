// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.view.ViewBox
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

/**
 * Public-facing wrapper used by ImageIO SPI adapters to render an SVG on demand at a
 * caller-chosen size. Encapsulates the intrinsic dimensions resolved from the parsed
 * document so that callers that don't supply an explicit size can fall back to them.
 */
@Internal
class JSvgDocument private constructor(
  private val parsed: ParsedSvgDocument,
  val width: Float,
  val height: Float,
) {

  companion object {

    @JvmStatic
    @Throws(IOException::class)
    fun create(inputStream: InputStream): JSvgDocument =
      create(createXmlStreamReader(inputStream))

    @JvmStatic
    @Throws(IOException::class)
    fun create(data: ByteArray): JSvgDocument =
      create(createXmlStreamReader(data))

    @JvmStatic
    @Throws(IOException::class)
    fun create(xmlStreamReader: XMLStreamReader2): JSvgDocument {
      val parsed = createJSvgDocument(xmlStreamReader)
      val (width, height) = resolveIntrinsicSize(parsed)
      return JSvgDocument(parsed, width, height)
    }

    /**
     * Resolves the intrinsic image dimensions IDEA should use when the caller doesn't
     * supply one. Preference order: explicit root `width`/`height`, then viewBox dimensions
     * (when positive), then the canonical 16-pixel icon fallback.
     */
    private fun resolveIntrinsicSize(parsed: ParsedSvgDocument): Pair<Float, Float> {
      val resolvedSize = parsed.document.size()
      val viewBox = parsed.document.viewBox()

      val width = when {
        parsed.rawWidth != null -> resolvedSize.width
        viewBox.width > 0 -> viewBox.width
        else -> ICON_FALLBACK_SIZE
      }
      val height = when {
        parsed.rawHeight != null -> resolvedSize.height
        viewBox.height > 0 -> viewBox.height
        else -> ICON_FALLBACK_SIZE
      }
      return width to height
    }

    private const val ICON_FALLBACK_SIZE: Float = 16f
  }

  fun createImage(preferredWidth: Int?, preferredHeight: Int?): BufferedImage {
    val width: Float
    val height: Float

    if (preferredHeight == null && preferredWidth == null) {
      width = this.width
      height = this.height
    }
    else if (preferredHeight != null && preferredWidth != null) {
      width = preferredWidth.toFloat()
      height = preferredHeight.toFloat()
    }
    else if (preferredHeight != null) {
      height = preferredHeight.toFloat()
      width = (height * this.width) / this.height
    }
    else {
      width = preferredWidth!!.toFloat()
      height = (width * this.height) / this.width
    }

    // how to have an hidpi aware image?
    val image = ImageUtil.createImage(
      ScaleContext.create(),
      width.toDouble(),
      height.toDouble(),
      BufferedImage.TYPE_INT_ARGB,
      PaintUtil.RoundingMode.ROUND,
    )
    val g = image.createGraphics()
    try {
      ImageUtil.applyQualityRenderingHints(g)
      parsed.document.render(null, g, ViewBox(width, height))
    }
    finally {
      g.dispose()
    }
    return image
  }
}
