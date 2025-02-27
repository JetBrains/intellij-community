// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.parser.PluginXmlStreamReader
import com.intellij.ide.plugins.parser.ReadModuleContext
import com.intellij.ide.plugins.parser.consume
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.util.xml.dom.NoOpXmlInterner
import java.nio.file.Path

fun readDescriptorForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val pathResolver = PluginXmlPathResolver.DEFAULT_PATH_RESOLVER
  val dataLoader = object : DataLoader {
    override fun load(path: String, pluginDescriptorSourceOnly: Boolean) = throw UnsupportedOperationException()

    override fun toString() = throw UnsupportedOperationException()
  }

  val raw = PluginXmlStreamReader(object : ReadModuleContext {
    override val interner = NoOpXmlInterner
  }, dataLoader, pathResolver, null, null).let {
    it.consume(input, path.toString())
    it.getRawPluginDescriptor()
  }
  if (id != null) {
    raw.id = id.idString
  }
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
  val raw = PluginXmlStreamReader(context, dataLoader, pathResolver, null, null).let {
    it.consume(data, path.toString())
    it.getRawPluginDescriptor()
  }
  val result = IdeaPluginDescriptorImpl(raw = raw, path = path, isBundled = isBundled, id = null, moduleName = null)
  initMainDescriptorByRaw(descriptor = result, raw = raw, pathResolver = pathResolver, context = context, dataLoader = dataLoader, pluginDir = path, pool = ZipFilePoolImpl())
  return result
}
