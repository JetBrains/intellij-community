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

import com.intellij.util.io.IOUtil
import org.jdom.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

private enum class TypeMarker {
  ELEMENT, CDATA, TEXT, ELEMENT_END
}

fun serializeElementToBinary(element: Element, out: OutputStream) {
  writeElement(element, DataOutputStream(out))
}

fun readElement(input: InputStream) = readElement(DataInputStream(input))

fun readElement(input: DataInputStream): Element {
  val element = Element(IOUtil.readUTF(input))
  readAttributes(element, input)
  readContent(element, input)
  return element
}

private fun readContent(element: Element, input: DataInputStream) {
  while (true) {
    when (input.read()) {
      TypeMarker.ELEMENT.ordinal -> element.addContent(readElement(input))
      TypeMarker.TEXT.ordinal -> element.addContent(Text(IOUtil.readUTF(input)))
      TypeMarker.CDATA.ordinal -> element.addContent(CDATA(IOUtil.readUTF(input)))
      TypeMarker.ELEMENT_END.ordinal -> return
    }
  }
}

private fun writeElement(element: Element, out: DataOutputStream) {
  IOUtil.writeUTF(out, element.name)

  writeAttributes(out, element.attributes)

  val content = element.content
  for (item in content) {
    if (item is Element) {
      out.writeByte(TypeMarker.ELEMENT.ordinal)
      writeElement(item, out)
    }
    else if (item is Text) {
      if (!isAllWhitespace(item)) {
        out.writeByte(TypeMarker.TEXT.ordinal)
        IOUtil.writeUTF(out, item.text)
      }
    }
    else if (item is CDATA) {
      out.writeByte(TypeMarker.CDATA.ordinal)
      IOUtil.writeUTF(out, item.text)
    }
  }
  out.writeByte(TypeMarker.ELEMENT_END.ordinal)
}

private fun writeAttributes(out: DataOutputStream, attributes: List<Attribute>?) {
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
      IOUtil.writeUTF(out, attribute.name)
      IOUtil.writeUTF(out, attribute.value)
    }
  }
}

private fun readAttributes(element: Element, input: DataInputStream) {
  val size = input.readUnsignedByte()
  for (i in 0..size - 1) {
    element.setAttribute(Attribute(IOUtil.readUTF(input), IOUtil.readUTF(input)))
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