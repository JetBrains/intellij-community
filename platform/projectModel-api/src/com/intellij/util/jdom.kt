// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import com.intellij.util.xmlb.Constants
import org.jdom.Document
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Parent
import org.jdom.filter.ElementFilter
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.nio.file.Path

@JvmOverloads
@Throws(IOException::class)
fun Parent.write(file: Path, lineSeparator: String = "\n") {
  write(file.outputStream(), lineSeparator)
}

@JvmOverloads
fun Parent.write(output: OutputStream, lineSeparator: String = "\n") {
  output.bufferedWriter().use { writer ->
    if (this is Document) {
      JDOMUtil.writeDocument(this, writer, lineSeparator)
    }
    else {
      JDOMUtil.writeElement(this as Element, writer, lineSeparator)
    }
  }
}

@Throws(IOException::class, JDOMException::class)
fun loadElement(chars: CharSequence): Element = JDOMUtil.load(chars)

@Throws(IOException::class, JDOMException::class)
fun loadElement(reader: Reader): Element = JDOMUtil.load(reader)

@Throws(IOException::class, JDOMException::class)
fun loadElement(stream: InputStream): Element = JDOMUtil.loadDocument(stream.bufferedReader()).detachRootElement()

@Throws(IOException::class, JDOMException::class)
fun loadElement(path: Path): Element = loadElement(path.inputStream())

fun Element?.isEmpty(): Boolean = this == null || JDOMUtil.isEmpty(this)

fun Element.getOrCreate(name: String): Element {
  var element = getChild(name)
  if (element == null) {
    element = Element(name)
    addContent(element)
  }
  return element
}

fun Element.get(name: String): Element? = getChild(name)

fun Element.element(name: String): Element {
  val element = Element(name)
  addContent(element)
  return element
}

fun Element.attribute(name: String, value: String?): Element = setAttribute(name, value)

fun <T> Element.remove(name: String, transform: (child: Element) -> T): List<T> {
  val result = SmartList<T>()
  val groupIterator = getContent(ElementFilter(name)).iterator()
  while (groupIterator.hasNext()) {
    val child = groupIterator.next()
    result.add(transform(child))
    groupIterator.remove()
  }
  return result
}

fun Element.toBufferExposingByteArray(lineSeparator: LineSeparator = LineSeparator.LF): BufferExposingByteArrayOutputStream {
  val out = BufferExposingByteArrayOutputStream(1024)
  JDOMUtil.write(this, out, lineSeparator.separatorString)
  return out
}

fun Element.toByteArray(): ByteArray {
  return toBufferExposingByteArray().toByteArray()
}

@JvmOverloads
fun Element.addOptionTag(name: String, value: String, elementName: String = Constants.OPTION) {
  val element = Element(elementName)
  element.setAttribute(Constants.NAME, name)
  element.setAttribute(Constants.VALUE, value)
  addContent(element)
}

fun Element.getAttributeBooleanValue(name: String): Boolean = java.lang.Boolean.parseBoolean(getAttributeValue(name))