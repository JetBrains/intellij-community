// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.nodes.SVG
import com.intellij.ui.paint.PaintUtil
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.xml.dom.createXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

@Internal
class JSvgDocument(
  private val document: SVG,
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

      val svg = createJSvgDocument(xmlStreamReader)

      val viewBox = SvgViewBox(svg)

      val width = svg.width.raw().takeIf { it.isFinite() }
                  ?: viewBox.width.takeIf { it > 0 }
                  ?: 16f
      val height = svg.height.raw().takeIf { it.isFinite() }
                   ?: viewBox.height.takeIf { it > 0 }
                   ?: 16f

      return JSvgDocument(svg, width, height)
    }
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
    val bi = ImageUtil.createImage(
      ScaleContext.create(),
      width.toDouble(),
      height.toDouble(),
      BufferedImage.TYPE_INT_ARGB,
      PaintUtil.RoundingMode.ROUND
    )
    val g = bi.createGraphics()

    ImageUtil.applyQualityRenderingHints(g)

    document.renderWithSize(
      width,
      height,
      SVGFont.defaultFontSize(),
      g,
    )
    return bi
  }

}