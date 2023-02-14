// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.configurationStore

import com.intellij.util.io.DataOutputStream
import com.intellij.util.io.IOUtil
import org.jdom.Attribute
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Text
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.*

internal enum class TypeMarker {
  ELEMENT, CDATA, TEXT, ELEMENT_END
}

fun serializeElementToBinary(element: Element, out: OutputStream) {
  BinaryXmlWriter(DataOutputStream(out)).write(element)
}

fun deserializeElementFromBinary(input: InputStream): Element = BinaryXmlReader(DataInputStream(input)).read()

private class BinaryXmlReader(private val input: DataInputStream) {
  private val strings = ArrayList<String>()

  fun read() = readElement()

  private fun readString(): String {
    val lengthOrIndex = readUInt29()
    if (lengthOrIndex == 1) {
      return ""
    }

    if ((lengthOrIndex and 1) == 1) {
      val string = IOUtil.readUTF(input)
      strings.add(string)
      return string
    }

    return strings.get(lengthOrIndex shr 1)
  }

  private fun readElement(): Element {
    val element = Element(readString())
    readAttributes(element)
    readContent(element)
    return element
  }

  private fun readContent(element: Element) {
    while (true) {
      when (input.read()) {
        TypeMarker.ELEMENT.ordinal -> element.addContent(readElement())
        TypeMarker.TEXT.ordinal -> element.addContent(Text(readString()))
        TypeMarker.CDATA.ordinal -> element.addContent(CDATA(readString()))
        TypeMarker.ELEMENT_END.ordinal -> return
      }
    }
  }

  private fun readAttributes(element: Element) {
    val size = input.readUnsignedByte()
    for (i in 0 until size) {
      element.setAttribute(Attribute(readString(), readString()))
    }
  }

  private fun readUInt29(): Int {
    var value: Int
    var b = input.read()
    if ((b and 0xFF) < 128) {
      return b
    }

    value = b and 0x7F shl 7
    b = input.read()
    if ((b and 0xFF) < 128) {
      return value or b
    }

    value = value or (b and 0x7F) shl 7
    b = input.read()
    if ((b and 0xFF) < 128) {
      return value or b
    }

    return value or (b and 0x7F) shl 8 or (input.read() and 0xFF)
  }
}