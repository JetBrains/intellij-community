// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.svg

import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ImageLoader
import org.jetbrains.annotations.ApiStatus
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.InputStream

@ApiStatus.Internal
fun renderSvg(inputStream: InputStream,
              scale: Float = JBUIScale.sysScale(),
              overriddenWidth: Float = -1f,
              overriddenHeight: Float = -1f,
              uri: String? = null): BufferedImage {
  return SvgTranscoder.createImage(scale = scale,
                                   document = createSvgDocument(inputStream = inputStream, uri = uri),
                                   overriddenWidth = overriddenWidth,
                                   overriddenHeight = overriddenHeight)

}

@ApiStatus.Internal
@JvmOverloads
@Throws(IOException::class)
fun renderSvg(data: ByteArray, scale: Float = JBUIScale.sysScale()): BufferedImage {
  return SvgTranscoder.createImage(scale = scale, document = createSvgDocument(data = data))
}

@ApiStatus.Internal
fun getSvgDocumentSize(data: ByteArray, scale: Float = JBUIScale.sysScale()): ImageLoader.Dimension2DDouble {
  return SvgTranscoder.getDocumentSize(scale = scale, document = createSvgDocument(data = data))
}