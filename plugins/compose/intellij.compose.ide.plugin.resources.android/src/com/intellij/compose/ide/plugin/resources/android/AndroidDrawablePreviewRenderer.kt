// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.android

import com.android.ide.common.vectordrawable.VdPreview
import com.intellij.compose.ide.plugin.resources.vectorDrawable.preview.BaseVectorDrawablePreviewRenderer
import com.intellij.openapi.diagnostic.Logger
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.IOException
import java.io.StringReader
import kotlin.math.roundToInt

/** Android implementation using VdPreview from Android SDK tools. */
class AndroidDrawablePreviewRenderer : BaseVectorDrawablePreviewRenderer() {
  override fun getVectorDrawableSizeDp(xmlContent: String): Dimension? {
    return try {
      val parser = createParser(xmlContent)
      if (!skipToStartTag(parser)) return null
      if (!isVectorElement(parser)) return null

      val width = parser.getDoubleAttributeValue("width")
      val height = parser.getDoubleAttributeValue("height")
      if (width.isNaN() || height.isNaN()) return null

      Dimension(width.roundToInt(), height.roundToInt())
    }
    catch (_: XmlPullParserException) {
      null
    }
    catch (_: IOException) {
      null
    }
  }

  override fun doRenderPreview(imageScale: Double, xmlContent: String, errors: StringBuilder): BufferedImage? {
    return try {
      val targetSize = VdPreview.TargetSize.createFromScale(imageScale)
      VdPreview.getPreviewFromVectorXml(targetSize, xmlContent, errors)
    }
    catch (e: Exception) {
      if (Logger.shouldRethrow(e)) throw e
      errors.append(e.message)
      null
    }
  }

  private fun createParser(drawable: String): KXmlParser =
    KXmlParser().apply {
      setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
      setInput(StringReader(drawable))
    }

  private fun skipToStartTag(parser: KXmlParser): Boolean {
    while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG) return true
    }
    return false
  }

  private fun isVectorElement(parser: KXmlParser): Boolean =
    parser.eventType == XmlPullParser.START_TAG && parser.name == "vector" && parser.prefix == null

  private fun KXmlParser.getDoubleAttributeValue(attributeName: String): Double =
    parseDoubleValue(getAttributeValue(ANDROID_URI, attributeName))

  private fun parseDoubleValue(value: String?): Double {
    if (value == null || !value.endsWith(DP_SUFFIX)) return Double.NaN

    return value.dropLast(DP_SUFFIX.length).toDoubleOrNull() ?: Double.NaN
  }

  companion object {
    private const val ANDROID_URI = "http://schemas.android.com/apk/res/android"
    private const val DP_SUFFIX = "dp"
  }
}