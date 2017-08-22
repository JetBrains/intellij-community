/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.util.containers.ObjectIntHashMap
import com.intellij.util.io.IOUtil
import org.jdom.*
import java.io.DataOutputStream

private fun String.isEmptySafe(): Boolean {
  return try {
    isEmpty()
  }
  catch (e: NullPointerException) {
    LOG.error(e)
    true
  }
}

internal class BinaryXmlWriter(private val out: DataOutputStream) {
  private val strings = ObjectIntHashMap<String>()

  fun write(element: Element) {
    writeElement(element)
  }

  private fun writeString(string: String) {
    if (string.isEmptySafe()) {
      out.write(1)
      return
    }

    val reference = strings.get(string)
    if (reference != -1) {
      writeUInt29(reference shl 1)
      return
    }

    strings.put(string, strings.size())
    // don't write actual length, IOUtil does it
    out.write((1 shl 1) or 1)
    IOUtil.writeUTF(out, string)
  }

  private fun writeElement(element: Element) {
    writeString(element.name)

    writeAttributes(element.attributes)

    val content = element.content
    for (item in content) {
      if (item is Element) {
        out.writeByte(TypeMarker.ELEMENT.ordinal)
        writeElement(item)
      }
      else if (item is Text) {
        if (!isAllWhitespace(item)) {
          out.writeByte(TypeMarker.TEXT.ordinal)
          writeString(item.text)
        }
      }
      else if (item is CDATA) {
        out.writeByte(TypeMarker.CDATA.ordinal)
        writeString(item.text)
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
    if (v < 0x80) {
      out.write(v)
    }
    else if (v < 0x4000) {
      out.write((v shr 7 and 0x7F or 0x80))
      out.write(v and 0x7F)
    }
    else if (v < 0x200000) {
      out.write((v shr 14 and 0x7F or 0x80))
      out.write(v shr 7 and 0x7F or 0x80)
      out.write(v and 0x7F)
    }
    else if (v < 0x40000000) {
      out.write(v shr 22 and 0x7F or 0x80)
      out.write (v shr 15 and 0x7F or 0x80)
      out.write(v shr 8 and 0x7F or 0x80)
      out.write(v and 0xFF)
    }
    else {
      throw IllegalArgumentException("Integer out of range: $v")
    }
  }

  private fun isAllWhitespace(obj: Content): Boolean {
    val str = (obj as? Text)?.text ?: return false
    for (i in 0..str.length - 1) {
      if (!Verifier.isXMLWhitespace(str[i])) {
        return false
      }
    }
    return true
  }
}