// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.github.weisj.jsvg.attributes.font.SVGFont
import com.github.weisj.jsvg.geometry.size.FloatSize
import com.github.weisj.jsvg.geometry.size.MeasureContext
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

@ApiStatus.Internal
fun renderSvg(inputStream: InputStream, scale: Float = JBUIScale.sysScale(), uri: String? = null): BufferedImage {
  return renderUsingJSvg(scale = scale, path = uri, document = createJSvgDocument(inputStream))
}

@ApiStatus.Internal
fun renderSvg(inputStream: InputStream,
              scale: Float = JBUIScale.sysScale(),
              baseWidth: Float,
              baseHeight: Float): BufferedImage {
  return renderUsingJSvg(scale = scale,
                         document = createJSvgDocument(inputStream),
                         baseWidth = baseWidth,
                         baseHeight = baseHeight)

}

@ApiStatus.Internal
@JvmOverloads
@Throws(IOException::class)
fun renderSvg(data: ByteArray, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return renderUsingJSvg(scale = scale, document = createJSvgDocument(data = data))
}

@ApiStatus.Internal
fun getSvgDocumentSize(data: ByteArray): Rectangle2D.Float {
  val document = createJSvgDocument(data = data)

  @Suppress("DuplicatedCode")
  val defaultEm = SVGFont.defaultFontSize()
  val topLevelContext = MeasureContext.createInitial(FloatSize(16f, 16f), defaultEm, SVGFont.exFromEm(defaultEm))
  val w = document.width.orElseIfUnspecified(16f).resolveWidth(topLevelContext)
  val h = document.height.orElseIfUnspecified(16f).resolveHeight(topLevelContext)
  return Rectangle2D.Float(0f, 0f, w, h)
}