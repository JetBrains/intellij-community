/*
 * Copyright (C) 2020 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.previewDrawables

import com.android.SdkConstants
import com.android.utils.CharSequences
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.awt.Dimension
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Based on: [com.android.tools.idea.rendering.VectorDrawableTransformer]
 */
internal object VectorDrawableUtils {
  /**
   * Parses the Android Vector XML to extract its intrinsic width and height.
   * Replaces the logic from com.android.tools.idea.rendering.VectorDrawableTransformer
   */
  fun getSizeDp(drawable: String): Dimension? {
    try {
      val parser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        setInput(CharSequences.getReader(drawable, true))
      }
      if (!skipToFirstTag(parser) || !isVectorElement(parser)) return null

      val width: Double = getDoubleAttributeValue(parser, "width", DP_SUFFIX)
      val height: Double = getDoubleAttributeValue(parser, "height", DP_SUFFIX)
      if (width.isNaN() || height.isNaN()) return null
      return Dimension(width.toFloat().roundToInt(), height.toFloat().roundToInt())
    }
    catch (_: XmlPullParserException) {
      return null
    }
    catch (_: IOException) {
      return null
    }
  }

  private fun skipToFirstTag(parser: KXmlParser): Boolean {
    while (parser.nextToken() != XmlPullParser.END_DOCUMENT) {
      if (parser.eventType == XmlPullParser.START_TAG) return true
    }
    return false
  }

  private fun isVectorElement(parser: KXmlParser): Boolean =
    parser.eventType == XmlPullParser.START_TAG && parser.name == "vector" && parser.prefix == null

  private fun getDoubleAttributeValue(parser: KXmlParser, attributeName: String, expectedSuffix: String): Double =
    parseDoubleValue(parser.getAttributeValue(SdkConstants.ANDROID_URI, attributeName), expectedSuffix)

  private fun parseDoubleValue(value: String?, expectedSuffix: String): Double {
    if (value == null || !value.endsWith(expectedSuffix)) return Double.NaN

    return value.dropLast(DP_SUFFIX.length).toDoubleOrNull() ?: Double.NaN
  }

  private const val DP_SUFFIX = "dp"
}
