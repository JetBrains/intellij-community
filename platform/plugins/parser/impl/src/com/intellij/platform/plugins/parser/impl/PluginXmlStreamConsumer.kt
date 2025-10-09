// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import org.codehaus.stax2.XMLStreamReader2
import java.io.InputStream
import javax.xml.stream.XMLStreamException

interface PluginXmlStreamConsumer {
  @Throws(XMLStreamException::class)
  fun consume(reader: XMLStreamReader2)
}

/**
 * Do not use [java.io.BufferedInputStream] - buffer is used internally already.
 */
@Throws(XMLStreamException::class)
fun PluginXmlStreamConsumer.consume(input: InputStream, locationSource: String?) {
  consume(createNonCoalescingXmlStreamReader(input = input, locationSource = locationSource))
}

@Throws(XMLStreamException::class)
fun PluginXmlStreamConsumer.consume(byteArray: ByteArray, locationSource: String?) {
  consume(createNonCoalescingXmlStreamReader(input = byteArray, locationSource = locationSource))
}
