// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.parser.impl

import org.codehaus.stax2.XMLStreamReader2
import java.io.InputStream

/**
 * Parses plugin XML descriptor from [input].
 * [input] should not be buffered because the buffering is done inside.
 * @param locationSource human-readable source of the XML, used for error reporting
 */
fun parsePluginXml(
  readContext: PluginDescriptorReaderContext,
  xIncludeLoader: XIncludeLoader?,
  input: InputStream,
  locationSource: String?,
): PluginDescriptorBuilder {
  val consumer = PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader)
  consumer.consume(input, locationSource)
  return consumer.getBuilder()
}

/**
 * Parses plugin XML descriptor from [input].
 * @param locationSource human-readable source of the XML, used for error reporting
 */
fun parsePluginXml(
  readContext: PluginDescriptorReaderContext,
  xIncludeLoader: XIncludeLoader?,
  input: ByteArray,
  locationSource: String?,
): PluginDescriptorBuilder {
  val consumer = PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader)
  consumer.consume(input, locationSource)
  return consumer.getBuilder()
}

/**
 * Parses plugin XML descriptor from [input].
 */
fun parsePluginXml(
  readContext: PluginDescriptorReaderContext,
  xIncludeLoader: XIncludeLoader?,
  input: XMLStreamReader2,
): PluginDescriptorBuilder {
  val consumer = PluginDescriptorFromXmlStreamConsumer(readContext, xIncludeLoader)
  consumer.consume(input)
  return consumer.getBuilder()
}