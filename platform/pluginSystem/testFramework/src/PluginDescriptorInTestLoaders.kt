// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.platform.pluginSystem.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.pluginSystem.parser.impl.RawPluginDescriptor
import com.intellij.platform.pluginSystem.parser.impl.XIncludeLoader
import com.intellij.util.xml.dom.createNonCoalescingXmlStreamReader
import java.nio.file.Path
import kotlin.io.path.inputStream
import kotlin.io.path.pathString

fun loadRawPluginDescriptorInTest(file: Path, xIncludeLoader: XIncludeLoader): RawPluginDescriptor {
  val xmlInput = createNonCoalescingXmlStreamReader(file.inputStream(), file.pathString)
  val rawPluginDescriptor = PluginDescriptorFromXmlStreamConsumer(ValidationPluginDescriptorReaderContext, xIncludeLoader).let {
    it.consume(xmlInput)
    it.build()
  }

  return rawPluginDescriptor
}