// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.plugins.parser.impl.PluginDescriptorFromXmlStreamConsumer
import com.intellij.platform.plugins.parser.impl.ReadModuleContext
import com.intellij.platform.plugins.parser.impl.consume
import com.intellij.platform.plugins.parser.impl.elements.OS
import com.intellij.util.xml.dom.NoOpXmlInterner
import java.nio.file.Path


fun readDescriptorForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val dataLoader = object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()

    override fun toString() = throw UnsupportedOperationException()
  }

  val rawBuilder = PluginDescriptorFromXmlStreamConsumer(object : ReadModuleContext {
    override val interner = NoOpXmlInterner
    override val elementOsFilter: (OS) -> Boolean
      get() = { it.convert().isSuitableForOs() }
  }, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(input, path.toString())
    it.getBuilder()
  }
  if (id != null) {
    rawBuilder.id = id.idString
  }
  val raw = rawBuilder.build()
  val result = IdeaPluginDescriptorImpl(raw = raw, path = path, isBundled = isBundled, id = id, moduleName = null)
  initMainDescriptorByRaw(
    descriptor = result,
    raw = raw,
    context = DescriptorListLoadingContext(customDisabledPlugins = emptySet()),
    pathResolver = pathResolver,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl(),
  )
  return result
}

fun createFromDescriptor(path: Path,
                         isBundled: Boolean,
                         data: ByteArray,
                         context: DescriptorListLoadingContext,
                         pathResolver: PathResolver,
                         dataLoader: DataLoader): IdeaPluginDescriptorImpl {
  val raw = PluginDescriptorFromXmlStreamConsumer(context, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(data, path.toString())
    it.build()
  }
  val result = IdeaPluginDescriptorImpl(raw = raw, path = path, isBundled = isBundled, id = null, moduleName = null)
  initMainDescriptorByRaw(descriptor = result, raw = raw, pathResolver = pathResolver, context = context, dataLoader = dataLoader, pluginDir = path, pool = ZipFilePoolImpl())
  return result
}