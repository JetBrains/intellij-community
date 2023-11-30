// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.util.text.CharSequenceReader
import com.intellij.util.xmlb.Constants
import org.jdom.Document
import org.jdom.Element
import org.jdom.JDOMException
import org.jdom.input.SAXBuilder
import org.jetbrains.annotations.NonNls
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import java.io.CharArrayReader
import java.io.IOException
import java.io.InputStream
import java.lang.ref.SoftReference

@Deprecated(message = "Use getOrCreateChild", replaceWith = ReplaceWith("getOrCreateChild"), DeprecationLevel.ERROR)
fun Element.getOrCreate(@NonNls name: String): Element {
  return getOrCreateChild(name)
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
fun Element.addOptionTag(@NonNls name: String, value: String, @NonNls elementName: String = Constants.OPTION) {
  val element = Element(elementName)
  element.setAttribute(Constants.NAME, name)
  element.setAttribute(Constants.VALUE, value)
  addContent(element)
}

@Suppress("DEPRECATION")
private val cachedSpecialSaxBuilder = ThreadLocal<SoftReference<SAXBuilder>>()

@Suppress("DEPRECATION")
private fun getSpecialSaxBuilder(): SAXBuilder {
  val reference = cachedSpecialSaxBuilder.get()
  var saxBuilder = reference?.get()
  if (saxBuilder == null) {
    saxBuilder = SAXBuilder()
    saxBuilder.setEntityResolver(EntityResolver { _, _ -> InputSource(CharArrayReader(ArrayUtilRt.EMPTY_CHAR_ARRAY)) })
    cachedSpecialSaxBuilder.set(SoftReference(saxBuilder))
  }
  return saxBuilder
}

@Throws(IOException::class, JDOMException::class)
fun loadDocumentAndKeepBoundaryWhitespace(stream: InputStream): Document {
  return stream.use { getSpecialSaxBuilder().build(it) }
}

fun loadElementAndKeepBoundaryWhitespace(chars: CharSequence): Element {
  return getSpecialSaxBuilder().build(CharSequenceReader(chars)).detachRootElement()
}

@Throws(IOException::class, JDOMException::class)
fun loadElementAndKeepBoundaryWhitespace(stream: InputStream): Element {
  return stream.use { getSpecialSaxBuilder().build(it) }.detachRootElement()
}