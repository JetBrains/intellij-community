// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.serializers

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
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

  private val colorSerializer = ColorId.serializer()

  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("SimpleTextAttributes") {
    element<ColorId>("bgColor")
    element<ColorId>("fgColor")
    element<ColorId>("waveColor")
    element<Int>("style")
  }

  override fun serialize(encoder: Encoder, value: SimpleTextAttributes) {
    encoder.encodeStructure(descriptor) {
      value.bgColor?.let { encodeColor(descriptor, 0, it) }
      value.fgColor?.let { encodeColor(descriptor, 1, it) }
      value.waveColor?.let { encodeColor(descriptor, 2, it) }
      encodeIntElement(descriptor, 3, value.style)
    }
  }

  private fun CompositeEncoder.encodeColor(descriptor: SerialDescriptor, index: Int, color: Color) {
    encodeSerializableElement(descriptor, index, colorSerializer, color.rpcId())
  }

  override fun deserialize(decoder: Decoder): SimpleTextAttributes {
    return decoder.decodeStructure(descriptor) {
      var bgColor: Color? = null
      var fgColor: Color? = null
      var waveColor: Color? = null
      var style = SimpleTextAttributes.STYLE_PLAIN

      while (true) {
        when (val index = decodeElementIndex(descriptor)) {
          0 -> bgColor = decodeSerializableElement(descriptor, 0, colorSerializer).color()
          1 -> fgColor = decodeSerializableElement(descriptor, 1, colorSerializer).color()
          2 -> waveColor = decodeSerializableElement(descriptor, 2, colorSerializer).color()
          3 -> style = decodeIntElement(descriptor, 3)
          -1 -> break
          else -> logger<SimpleTextAttributesSerializer>().error("Unexpected index: $index")
        }
      }

      SimpleTextAttributes(bgColor, fgColor, waveColor, style)
    }
  }
}