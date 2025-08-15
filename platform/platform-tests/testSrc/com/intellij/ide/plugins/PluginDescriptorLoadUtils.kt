// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.PluginDescriptorReaderContext
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.xml.dom.NoOpXmlInterner
import java.nio.file.Path


fun readDescriptorFromBytesForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val loadingContext = PluginDescriptorLoadingContext()
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val dataLoader = object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()
    override fun toString() = throw UnsupportedOperationException()
  }
  val rawBuilder = PluginDescriptorFromXmlStreamConsumer(object : PluginDescriptorReaderContext {
    override val interner = NoOpXmlInterner
    override val isMissingIncludeIgnored = false
    override val elementOsFilter: (OS) -> Boolean
      get() = { it.convert().isSuitableForOs() }
  }, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(input, path.toString())
    it.getBuilder()
  }
  loadingContext.patchPlugin(rawBuilder)
  if (id != null) {
    rawBuilder.id = id.idString
  }
  val raw = rawBuilder.build()
  val result = PluginMainDescriptor(raw = raw, pluginPath = path, isBundled = isBundled)
  loadPluginSubDescriptors(
    descriptor = result,
    pathResolver = pathResolver,
    loadingContext = loadingContext,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl(),
  )
  return result
}

fun readDescriptorFromBytesForTest(
  path: Path,
  isBundled: Boolean,
  data: ByteArray,
  loadingContext: PluginDescriptorLoadingContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
): PluginMainDescriptor {
  val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext.readContext, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(data, path.toString())
    loadingContext.patchPlugin(it.getBuilder())
    it.build()
  }
  val result = PluginMainDescriptor(raw = raw, pluginPath = path, isBundled = isBundled)
  loadPluginSubDescriptors(
    descriptor = result,
    pathResolver = pathResolver,
    loadingContext = loadingContext,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl()
  )
  return result
}