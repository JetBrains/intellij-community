// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.testFramework

import com.intellij.ide.plugins.DiscoveredPluginsList
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginDescriptorLoadingResult
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.ide.plugins.PluginLoadingResult
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.loadDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.Collections.emptySet

class PluginSetTestBuilder(private val path: Path) {
  private var disabledPluginIds = mutableSetOf<PluginId>()
  private var expiredPluginIds = mutableSetOf<PluginId>()
  private var brokenPlugins = mutableMapOf<PluginId, MutableSet<String?>>()
  private var productBuildNumber = PluginManagerCore.buildNumber

  fun withDisabledPlugins(vararg disabledPluginIds: String) = apply {
    this.disabledPluginIds += disabledPluginIds.map(PluginId::getId)
  }

  fun withExpiredPlugins(vararg expiredPluginIds: String) = apply {
    this.expiredPluginIds += expiredPluginIds.map(PluginId::getId)
  }

  fun withBrokenPlugin(pluginId: String, vararg versions: String?) = apply {
    brokenPlugins.computeIfAbsent(PluginId.getId(pluginId), { mutableSetOf() }).addAll(versions)
  }

  fun withProductBuildNumber(productBuildNumber: BuildNumber) = apply {
    this.productBuildNumber = productBuildNumber
  }

  var buildNumber: String
    get() = productBuildNumber.toString()
    set(value) {
      productBuildNumber = BuildNumber.fromString(value)!!
    }

  fun buildInitContext(): PluginInitializationContext {
    // copy just in case
    val buildNumber = productBuildNumber
    return PluginInitializationContext.buildForTest(
      essentialPlugins = emptySet(),
      disabledPlugins = disabledPluginIds.toSet(),
      expiredPlugins = expiredPluginIds.toSet(),
      brokenPluginVersions = brokenPlugins.mapValues { it.value.toSet() }.toMap(),
      getProductBuildNumber = { buildNumber },
      requirePlatformAliasDependencyForLegacyPlugins = false,
      checkEssentialPlugins = false,
      explicitPluginSubsetToLoad = null,
      disablePluginLoadingCompletely = false,
    )
  }

  fun buildLoadingResult(initContext: PluginInitializationContext? = null): Pair<PluginDescriptorLoadingContext, PluginLoadingResult> {
    val initContext = initContext ?: buildInitContext()
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val result = PluginLoadingResult()
    // constant order in tests
    val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
    val descriptors = paths.mapNotNull { path -> loadDescriptor(path, loadingContext, ZipFilePoolImpl()) }
    val pluginList = DiscoveredPluginsList(descriptors, PluginsSourceContext.Custom)
    loadingContext.use {
      runBlocking {
        result.initAndAddAll(
          descriptorLoadingResult = PluginDescriptorLoadingResult.build(listOf(pluginList)),
          initContext = initContext,
        )
      }
    }
    return loadingContext to result
  }

  fun build(): PluginSet {
    val initContext = buildInitContext()
    val (loadingContext, loadingResult) = buildLoadingResult(initContext)
    return PluginManagerCore.initializePlugins(
      descriptorLoadingErrors = loadingContext.copyDescriptorLoadingErrors(),
      initContext = initContext,
      loadingResult = loadingResult,
      coreLoader = UrlClassLoader.build().get(),
      parentActivity = null,
    ).pluginSet
  }
}