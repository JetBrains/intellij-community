// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.intellij.ui.svg

import org.jetbrains.annotations.ApiStatus.Internal
import sun.awt.image.ImageDecoder
import sun.awt.image.InputStreamImageSource
import java.awt.image.ImageConsumer
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

@Internal
class SvgImageDecoder(
  src: InputStreamImageSource,
  private val `is`: InputStream,
  private val preferredWidth: Int,
  private val preferredHeight: Int,
) : ImageDecoder(src, `is`) {

  override fun produceImage() {
    try {
      val jSvgDocument = try {
        JSvgDocument.create(`is`)
      }
      catch (e: Exception) {
        throw IOException("Cannot load SVG document", e)
      }

      // how to have an hidpi aware image?
      val bi = jSvgDocument.createImage(preferredWidth.takeIf { it > 0 }, preferredHeight.takeIf { it > 0 })

      setDimensions(bi.width, bi.height)
      setColorModel(bi.colorModel)

      val pixels = bi.raster.getDataElements(0, 0, bi.width, bi.height, null) as IntArray
      setPixels(0, 0, bi.width, bi.height, bi.colorModel, pixels, 0, bi.width)

      imageComplete(ImageConsumer.STATICIMAGEDONE, true)
    }
    catch (e: IOException) {
      if (!aborted) {
        imageComplete(ImageConsumer.IMAGEERROR or ImageConsumer.STATICIMAGEDONE, true)
        throw e
      }
    }
    finally {
      try {
        close();
      }
      catch (_: Throwable) {
      }
    }
  }

  companion object {
    fun detect(src: InputStreamImageSource, stream: InputStream, preferredWidth: Int, preferredHeight: Int): SvgImageDecoder? {
      if (!isSvgDocument(stream)) {
        return null
      }
      return SvgImageDecoder(src, stream, preferredWidth, preferredHeight)
    }

    fun isSvgDocument(stream: InputStream): Boolean {
      // NOTE: This test is quite quick as it does not involve any parsing,
      // however, it may not recognize all kinds of SVG documents.

      // We need to read the first few bytes to determine if this is an SVG file,
      // then reset the stream at the marked position
      stream.mark(-1)
      try {
        // SVG file can starts with an XML declaration, then possibly followed by comments, whitespaces
        // \w.*(<?xml version="1.0" encoding="UTF-8" ?>)?
        // (\w|(<!--.*-->))*

        // Then either the doctype gives the hint possibly surrounded by comments and/or whitespaces
        // <!DOCTYPE svg ...
        // (\w|(<!--.*-->))*

        // Or the root tag is svg, possibly preceding comments and/or whitespaces
        // <svg ...


        // Handles first whitespaces if any
        val lastReadByte = stream.readFirstAfterWhitespaces()
        // The next byte should be '<' (comments, doctype XML declaration or the root tag)
        if (lastReadByte != '<'.code) {
          return false
        }

        val window = ByteArray(4)
        while (true) {
          stream.read(window).also { if (it < 0) return false }

          when {
            // `<?` Handles the XML declaration
            window.startsWith("?") -> stream.skipUntil("?>")

            // `<!--` Handles a comment
            window.startsWith("!--") -> stream.skipUntil("-->")

            // `<!DOCTYPE` Handles the DOCTYPE declaration
            window.startsWith("!DOC") && stream.readNextEquals("TYPE") -> {
              val lastReadChar = stream.readFirstAfterWhitespaces()
              return lastReadChar == 's'.code && stream.readNextEquals("vg")
            }

            // `<svg` Handles the root tag
            window.startsWith("svg") && (Char(window[3].toUShort()).isWhitespace() || window[3] == ':'.code.toByte()) -> {
              return true
            }

            // not an SVG file or not handled
            else -> return false
          }

          // Skip over, until next begin tag or EOF
          stream.skipUntil("<")
        }
      }
      catch (ignore: EOFException) {
        // ignore
        return false
      }
      finally {
        stream.reset()
      }
    }

  }
}

private fun ByteArray.startsWith(chars: String): Boolean {
  chars.forEachIndexed { index, c ->
    if (this[index] != c.code.toByte()) return false
  }
  return true
}

private fun InputStream.readFirstAfterWhitespaces(): Int {
  var lastReadByte: Int
  while ((read().also { lastReadByte = it }).toChar().isWhitespace()) {
    // skip whitespaces if any
  }
  return lastReadByte
}

private fun InputStream.skipUntil(chars: String) {
  if (chars.length == 1) {
    while (read().also { if (it < 0) return }.toChar() != chars[0]) {
      // skip until expected chars or EOF
    }
  }
  else {
    val buffer = StringBuffer()
    while (buffer.length < chars.length) {
      buffer.append(read().also { if (it < 0) return }.toChar())
    }
    while (!buffer.startsWith(chars)) {
      buffer.deleteCharAt(0)
      buffer.append(read().also { if (it < 0) return }.toChar())
    }
  }
}

private fun InputStream.readNextEquals(expected: String): Boolean {
  require(expected.isNotEmpty())
  expected.forEach {
    val read = read()
    if (read != it.code) return false
  }
  return true
}