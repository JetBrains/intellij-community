// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("StaxFactory")
package com.intellij.util.xml.dom

import com.fasterxml.aalto.`in`.ByteSourceBootstrapper
import com.fasterxml.aalto.`in`.CharSourceBootstrapper
import com.fasterxml.aalto.`in`.ReaderConfig
import com.fasterxml.aalto.stax.StreamReaderImpl
import org.codehaus.stax2.XMLInputFactory2
import org.codehaus.stax2.XMLStreamReader2
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.InputStream
import java.io.Reader
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamException

private val config = createConfig(coalesce = false)
private val configWithCoalescing = createConfig(coalesce = true)

private fun createConfig(coalesce: Boolean): ReaderConfig {
  val config = ReaderConfig()
  config.doAutoCloseInput(true)
  config.setProperty(XMLInputFactory.SUPPORT_DTD, false)
  config.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false)
  config.setProperty(XMLInputFactory2.P_INTERN_NAMES, false)
  config.setProperty(XMLInputFactory2.P_INTERN_NS_URIS, false)
  config.doPreserveLocation(false)
  config.setProperty(XMLInputFactory2.P_AUTO_CLOSE_INPUT, true)
  config.setXmlEncoding("UTF-8")
  config.doCoalesceText(coalesce)
  config.doParseLazily(true)
  return config
}

@Throws(XMLStreamException::class)
@JvmOverloads
fun createXmlStreamReader(input: InputStream, locationSource: String? = null): XMLStreamReader2 {
  return StreamReaderImpl.construct(ByteSourceBootstrapper.construct(configWithCoalescing.createNonShared(null, locationSource, "UTF-8"), input))
}

@Throws(XMLStreamException::class)
fun createXmlStreamReader(bytes: ByteArray): XMLStreamReader2 {
  return StreamReaderImpl.construct(ByteSourceBootstrapper.construct(configWithCoalescing.createNonShared(null, null, "UTF-8"), bytes, 0, bytes.size))
}

@Internal
fun createXmlStreamReader(bytes: ByteArray, start: Int, size: Int): XMLStreamReader2 {
  return StreamReaderImpl.construct(ByteSourceBootstrapper.construct(configWithCoalescing.createNonShared(null, null, "UTF-8"), bytes, start, size))
}

@Throws(XMLStreamException::class)
fun createNonCoalescingXmlStreamReader(input: InputStream, locationSource: String?): XMLStreamReader2 {
  return StreamReaderImpl.construct(ByteSourceBootstrapper.construct(config.createNonShared(null, locationSource, "UTF-8"), input))
}

@Throws(XMLStreamException::class)
fun createNonCoalescingXmlStreamReader(input: ByteArray, locationSource: String?): XMLStreamReader2 {
  return StreamReaderImpl.construct(ByteSourceBootstrapper.construct(config.createNonShared(null, locationSource, "UTF-8"), input, 0, input.size))
}

/**
 * Consider passing [InputStream] but not [Reader].
 */
@Throws(XMLStreamException::class)
fun createXmlStreamReader(reader: Reader): XMLStreamReader2 {
  return StreamReaderImpl.construct(CharSourceBootstrapper.construct(configWithCoalescing.createNonShared(null, null, "UTF-8"), reader))
}

fun createXmlStreamReader(chars: CharArray): XMLStreamReader2 {
  return StreamReaderImpl.construct(CharSourceBootstrapper.construct(configWithCoalescing.createNonShared(null, null, "UTF-8"), chars, 0, chars.size))
}
