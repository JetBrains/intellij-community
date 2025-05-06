// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.plugins.parser.impl.PluginDescriptorBuilder
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.io.write
import com.intellij.util.xml.dom.NoOpXmlInterner
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

fun plugin(outDir: Path, @Language("XML") descriptor: String) {
  val rawDescriptor = try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("${rawDescriptor.id!!}/${PluginManagerCore.PLUGIN_XML_PATH}").write(descriptor.trimIndent())
}

fun dependencyXml(outDir: Path, ownerId: String, filename: String, @Language("XML") descriptor: String) {
   try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("${ownerId}/${PluginManagerCore.META_INF}${filename}").write(descriptor.trimIndent())
}

fun module(outDir: Path, ownerId: String, moduleId: String, @Language("XML") descriptor: String) {
  try {
    readModuleDescriptorForTest(descriptor.toByteArray())
  }
  catch (e: Throwable) {
    throw RuntimeException("Cannot parse:\n ${descriptor.trimIndent().prependIndent("  ")}", e)
  }
  outDir.resolve("$ownerId/$moduleId.xml").write(descriptor.trimIndent())
}

@TestOnly
fun readModuleDescriptorForTest(input: ByteArray): PluginDescriptorBuilder {
  return PluginDescriptorFromXmlStreamConsumer(readContext = object : ReadModuleContext {
    override val interner = NoOpXmlInterner
    override val isMissingIncludeIgnored = false
    override val elementOsFilter: (OS) -> Boolean
      get() = { it.convert().isSuitableForOs() }
  }, xIncludeLoader = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER.toXIncludeLoader(object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()
    override fun toString() = ""
  })).let {
    it.consume(input, null)
    it.getBuilder()
  }
}
