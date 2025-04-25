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


fun readAndInitDescriptorFromBytesForTest(path: Path, isBundled: Boolean, input: ByteArray, id: PluginId? = null): IdeaPluginDescriptorImpl {
  val loadingContext = PluginDescriptorLoadingContext()
  val initContext = PluginInitializationContext.buildForTest(
    essentialPlugins = emptySet(),
    disabledPlugins = emptySet(),
    expiredPlugins = emptySet(),
    brokenPluginVersions = emptyMap(),
    getProductBuildNumber = { PluginManagerCore.buildNumber },
    requirePlatformAliasDependencyForLegacyPlugins = false,
    checkEssentialPlugins = false,
    explicitPluginSubsetToLoad = null,
    disablePluginLoadingCompletely = false,
  )
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
  loadingContext.patchPlugin(rawBuilder)
  if (id != null) {
    rawBuilder.id = id.idString
  }
  val raw = rawBuilder.build()
  val result = IdeaPluginDescriptorImpl(raw = raw, pluginPath = path, isBundled = isBundled)
  loadPluginSubDescriptors(
    descriptor = result,
    pathResolver = pathResolver,
    loadingContext = loadingContext,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl(),
  )
  return result.apply { initialize(context = initContext) }
}

fun readAndInitDescriptorFromBytesForTest(
  path: Path,
  isBundled: Boolean,
  data: ByteArray,
  loadingContext: PluginDescriptorLoadingContext,
  initContext: PluginInitializationContext,
  pathResolver: PathResolver,
  dataLoader: DataLoader,
): IdeaPluginDescriptorImpl {
  val raw = PluginDescriptorFromXmlStreamConsumer(loadingContext, pathResolver.toXIncludeLoader(dataLoader)).let {
    it.consume(data, path.toString())
    loadingContext.patchPlugin(it.getBuilder())
    it.build()
  }
  val result = IdeaPluginDescriptorImpl(raw = raw, pluginPath = path, isBundled = isBundled)
  loadPluginSubDescriptors(
    descriptor = result,
    pathResolver = pathResolver,
    loadingContext = loadingContext,
    dataLoader = dataLoader,
    pluginDir = path,
    pool = ZipFilePoolImpl()
  )
  return result.apply { initialize(context = initContext) }
}