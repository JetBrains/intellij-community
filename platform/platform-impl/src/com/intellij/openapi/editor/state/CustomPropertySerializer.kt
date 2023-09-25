// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

import com.intellij.ui.JBColor
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import java.awt.Color
import kotlin.reflect.typeOf

interface CustomPropertySerializer<T> {

  /**
   * @throws kotlinx.serialization.SerializationException
   * @throws IllegalArgumentException
   */
  fun encodeToString(obj: T): String

  /**
   * @throws kotlinx.serialization.SerializationException
   * @throws IllegalArgumentException
   */
  fun decodeFromString(encoded: String): T

}

object ColorPropertySerializer : CustomPropertySerializer<Color?> {
  override fun encodeToString(obj: Color?): String {
    if (obj == null) return "null"

    val toEncode = if (obj is JBColor) JBColorSerializable(true, obj.rgb, obj.darkVariant.rgb)
    else JBColorSerializable(false, obj.rgb, 0)

    return commonFormat.encodeToString(serializer(typeOf<JBColorSerializable>()), toEncode)
  }

  override fun decodeFromString(encoded: String): Color? {
    if (encoded == "null") return null

    val o = commonFormat.decodeFromString(serializer(typeOf<JBColorSerializable>()), encoded) as JBColorSerializable
    return if (o.isDarkAware) {
      JBColor(o.regularColorRgba, o.darkColorRgba)
    }
    else {
      @Suppress("UseJBColor")
      Color(o.regularColorRgba)
    }
  }

}

@Serializable
private class JBColorSerializable(val isDarkAware: Boolean,
                                  val regularColorRgba: Int,
                                  val darkColorRgba: Int)
