// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes
import fleet.util.logging.logger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import java.awt.Color

internal object SimpleTextAttributesSerializer : KSerializer<SimpleTextAttributes> {

  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SimpleTextAttributes") {
    element<Int?>("bgColor")
    element<Int?>("bgColorDark")
    element<Int?>("fgColor")
    element<Int?>("fgColorDark")
    element<Int?>("waveColor")
    element<Int?>("waveColorDark")
    element<Int>("style")
  }

  override fun serialize(encoder: Encoder, value: SimpleTextAttributes) {
    encoder.encodeStructure(descriptor) {
      value.bgColor?.let { encodeColor(it, 0) }
      value.fgColor?.let { encodeColor(it, 2) }
      value.waveColor?.let { encodeColor(it, 4) }
      encodeIntElement(descriptor, 6, value.style)
    }
  }

  private fun CompositeEncoder.encodeColor(color: Color, lightIndex: Int) {
    encodeIntElement(descriptor, lightIndex, color.rgb)
    if (color is JBColor) {
      color.darkVariant?.rgb?.let { encodeIntElement(descriptor, lightIndex + 1, it) }
    }
  }

  override fun deserialize(decoder: Decoder): SimpleTextAttributes {
    return decoder.decodeStructure(descriptor) {
      var bgColor: Int? = null
      var bgColorDark: Int? = null
      var fgColor: Int? = null
      var fgColorDark: Int? = null
      var waveColor: Int? = null
      var waveColorDark: Int? = null
      var style = SimpleTextAttributes.STYLE_PLAIN

      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> bgColor = decodeIntElement(descriptor, 0)
          1 -> bgColorDark = decodeIntElement(descriptor, 1)
          2 -> fgColor = decodeIntElement(descriptor, 2)
          3 -> fgColorDark = decodeIntElement(descriptor, 3)
          4 -> waveColor = decodeIntElement(descriptor, 4)
          5 -> waveColorDark = decodeIntElement(descriptor, 5)
          6 -> style = decodeIntElement(descriptor, 6)
          -1 -> break
          else -> logger<SimpleTextAttributesSerializer>().error("Unexpected index: $index")
        }
      }

      SimpleTextAttributes(
        bgColor?.let { JBColor(it, bgColorDark ?: it) },
        fgColor?.let { JBColor(it, fgColorDark ?: it) },
        waveColor?.let { JBColor(it, waveColorDark ?: it) },
        style,
      )
    }
  }
}
