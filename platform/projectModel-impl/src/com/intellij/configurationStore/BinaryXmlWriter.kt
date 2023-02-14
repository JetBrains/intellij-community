// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.util.io.IOUtil
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import org.jdom.*
import java.io.DataOutputStream

internal class BinaryXmlWriter(private val out: DataOutputStream) {
  private val strings = Object2IntOpenHashMap<String>()

  init {
    strings.defaultReturnValue(-1)
  }

  fun write(element: Element) {
    writeElement(element)
  }

  private fun writeString(string: String?) {
    if (string.isNullOrEmpty()) {
      out.write(1)
      return
    }

    val reference = strings.getInt(string)
    if (reference != -1) {
      writeUInt29(reference shl 1)
      return
    }

    strings.put(string, strings.size)
    // don't write actual length, IOUtil does it
    out.write((1 shl 1) or 1)
    IOUtil.writeUTF(out, string)
  }

  private fun writeElement(element: Element) {
    writeString(element.name)

    writeAttributes(if (element.hasAttributes()) element.attributes else null)

    val content = element.content
    for (item in content) {
      when (item) {
        is Element -> {
          out.writeByte(TypeMarker.ELEMENT.ordinal)
          writeElement(item)
        }
        is CDATA -> {
          out.writeByte(TypeMarker.CDATA.ordinal)
          writeString(item.text)
        }
        is Text -> {
          val text = item.text
          if (text != null && !Verifier.isAllXMLWhitespace(text)) {
            out.writeByte(TypeMarker.TEXT.ordinal)
            writeString(text)
          }
        }
      }
    }
    out.writeByte(TypeMarker.ELEMENT_END.ordinal)
  }

  private fun writeAttributes(attributes: List<Attribute>?) {
    val size = attributes?.size ?: 0
    out.write(size)
    if (size == 0) {
      return
    }

    if (size > 255) {
      throw UnsupportedOperationException("attributes size > 255")
    }
    else {
      for (attribute in attributes!!) {
        writeString(attribute.name)
        writeString(attribute.value)
      }
    }
  }

  // Represent smaller integers with fewer bytes using the most significant bit of each byte. The worst case uses 32-bits
  // to represent a 29-bit number, which is what we would have done with no compression.
  private fun writeUInt29(v: Int) {
    when {
      v < 0x80 -> out.write(v)
      v < 0x4000 -> {
        out.write((v shr 7 and 0x7F or 0x80))
        out.write(v and 0x7F)
      }
      v < 0x200000 -> {
        out.write((v shr 14 and 0x7F or 0x80))
        out.write(v shr 7 and 0x7F or 0x80)
        out.write(v and 0x7F)
      }
      v < 0x40000000 -> {
        out.write(v shr 22 and 0x7F or 0x80)
        out.write (v shr 15 and 0x7F or 0x80)
        out.write(v shr 8 and 0x7F or 0x80)
        out.write(v and 0xFF)
      }
      else -> throw IllegalArgumentException("Integer out of range: $v")
    }
  }
}