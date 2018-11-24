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
package com.intellij.util

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.reference.SoftReference
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import com.intellij.util.text.CharSequenceReader
import org.jdom.Document
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.Parent
import org.jdom.filter.ElementFilter
import org.jdom.input.SAXBuilder
import org.jdom.input.SAXHandler
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.XMLReader
import java.io.*
import java.nio.file.Path
import javax.xml.XMLConstants

private val cachedSaxBuilder = ThreadLocal<SoftReference<SAXBuilder>>()

private fun getSaxBuilder(): SAXBuilder {
  val reference = cachedSaxBuilder.get()
  var saxBuilder = SoftReference.dereference<SAXBuilder>(reference)
  if (saxBuilder == null) {
    saxBuilder = object : SAXBuilder() {
      override fun configureParser(parser: XMLReader, contentHandler: SAXHandler?) {
        super.configureParser(parser, contentHandler)
        try {
          parser.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        }
        catch (ignore: Exception) {
        }
      }
    }
    saxBuilder.ignoringBoundaryWhitespace = true
    saxBuilder.ignoringElementContentWhitespace = true
    saxBuilder.entityResolver = EntityResolver { publicId, systemId -> InputSource(CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY)) }
    cachedSaxBuilder.set(SoftReference<SAXBuilder>(saxBuilder))
  }
  return saxBuilder
}

@JvmOverloads
@Throws(IOException::class)
fun Parent.write(file: Path, lineSeparator: String = "\n", filter: JDOMUtil.ElementOutputFilter? = null) {
  write(file.outputStream(), lineSeparator, filter)
}

@JvmOverloads
fun Parent.write(output: OutputStream, lineSeparator: String = "\n", filter: JDOMUtil.ElementOutputFilter? = null) {
  output.bufferedWriter().use { writer ->
    if (this is Document) {
      JDOMUtil.writeDocument(this, writer, lineSeparator)
    }
    else {
      JDOMUtil.writeElement(this as Element, writer, JDOMUtil.createOutputter(lineSeparator, filter))
    }
  }
}

@Throws(IOException::class, JDOMException::class)
fun loadElement(chars: CharSequence) = loadElement(CharSequenceReader(chars))

@Throws(IOException::class, JDOMException::class)
fun loadElement(reader: Reader): Element = loadDocument(reader).detachRootElement()

@Throws(IOException::class, JDOMException::class)
fun loadElement(stream: InputStream): Element = loadDocument(stream.reader()).detachRootElement()

@Throws(IOException::class, JDOMException::class)
fun loadElement(path: Path): Element = loadDocument(path.inputStream().bufferedReader()).detachRootElement()

fun loadDocument(reader: Reader): Document = reader.use { getSaxBuilder().build(it) }

fun Element?.isEmpty() = this == null || JDOMUtil.isEmpty(this)

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

fun Element.toByteArray(): ByteArray {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.write(this, out, "\n")
  return out.toByteArray()
}

fun Element.addOptionTag(name: String, value: String) {
  val element = Element("option")
  element.setAttribute("name", name)
  element.setAttribute("value", value)
  addContent(element)
}

fun Parent.toBufferExposingByteArray(lineSeparator: String = "\n"): BufferExposingByteArrayOutputStream {
  val out = BufferExposingByteArrayOutputStream(512)
  JDOMUtil.write(this, out, lineSeparator)
  return out
}