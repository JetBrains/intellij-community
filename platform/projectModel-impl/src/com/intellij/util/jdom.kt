/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.text.CharSequenceReader
import org.jdom.Document
import org.jdom.Element
import org.jdom.filter.ElementFilter
import org.jdom.input.SAXBuilder
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.CharArrayReader
import java.io.InputStream
import java.io.Reader
import java.nio.file.Files
import java.nio.file.Path

private val cachedSaxBuilder = ThreadLocal<SoftReference<SAXBuilder>>()

private fun getSaxBuilder(): SAXBuilder {
  val reference = cachedSaxBuilder.get()
  var saxBuilder = SoftReference.dereference<SAXBuilder>(reference)
  if (saxBuilder == null) {
    saxBuilder = SAXBuilder()
    saxBuilder.ignoringBoundaryWhitespace = true
    saxBuilder.ignoringElementContentWhitespace = true
    saxBuilder.entityResolver = EntityResolver { publicId, systemId -> InputSource(CharArrayReader(ArrayUtil.EMPTY_CHAR_ARRAY)) }
    cachedSaxBuilder.set(SoftReference<SAXBuilder>(saxBuilder))
  }
  return saxBuilder
}

fun loadElement(chars: CharSequence) = loadElement(CharSequenceReader(chars))

fun loadElement(reader: Reader): Element = loadDocument(reader).detachRootElement()

fun loadElement(stream: InputStream): Element = loadDocument(stream.reader()).detachRootElement()

fun loadElement(path: Path): Element = loadDocument(Files.newInputStream(path).bufferedReader()).detachRootElement()

private fun loadDocument(reader: Reader): Document {
  try {
    return getSaxBuilder().build(reader)
  }
  finally {
    reader.close()
  }
}

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
  JDOMUtil.writeParent(this, out, "\n")
  return out.toByteArray()
}