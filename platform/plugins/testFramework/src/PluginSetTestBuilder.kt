// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.testFramework

import com.intellij.ide.plugins.*
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.util.Collections.emptySet

class PluginSetTestBuilder private constructor(
  private val pluginDescriptorLoader: (loadingContext: PluginDescriptorLoadingContext) -> List<PluginMainDescriptor>,
) {
  private var disabledPluginIds = mutableSetOf<PluginId>()
  private var expiredPluginIds = mutableSetOf<PluginId>()
  private var brokenPlugins = mutableMapOf<PluginId, MutableSet<String?>>()
  private var productBuildNumber = PluginManagerCore.buildNumber
  private var customCoreLoader: UrlClassLoader? = null
  private var productMode: ProductMode = ProductMode.MONOLITH

  companion object {
    @JvmStatic
    fun fromPath(path: Path): PluginSetTestBuilder = PluginSetTestBuilder { loadingContext ->
      // constant order in tests
      val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
      paths.mapNotNull { path -> loadDescriptorFromFileOrDir(path, loadingContext, ZipFilePoolImpl()) }
    }

    @JvmStatic
    fun fromDescriptors(pluginDescriptorLoader: (loadingContext: PluginDescriptorLoadingContext) -> List<PluginMainDescriptor>): PluginSetTestBuilder {
      return PluginSetTestBuilder(pluginDescriptorLoader)
    }
  }

  fun withDisabledPlugins(vararg disabledPluginIds: String): PluginSetTestBuilder = apply {
    this.disabledPluginIds += disabledPluginIds.map(PluginId::getId)
  }

  fun withExpiredPlugins(vararg expiredPluginIds: String): PluginSetTestBuilder = apply {
    this.expiredPluginIds += expiredPluginIds.map(PluginId::getId)
  }

  fun withBrokenPlugin(pluginId: String, vararg versions: String?): PluginSetTestBuilder = apply {
    brokenPlugins.computeIfAbsent(PluginId.getId(pluginId)) { mutableSetOf() }.addAll(versions)
  }

  fun withProductBuildNumber(productBuildNumber: BuildNumber): PluginSetTestBuilder = apply {
    this.productBuildNumber = productBuildNumber
  }

  fun withCustomCoreLoader(loader: UrlClassLoader): PluginSetTestBuilder = apply {
    customCoreLoader = loader
  }
  
  fun withProductMode(productMode: ProductMode): PluginSetTestBuilder = apply {
    this.productMode = productMode
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
      currentProductModeId = productMode.id,
    )
  }

  fun buildLoadingResult(initContext: PluginInitializationContext? = null): Pair<PluginDescriptorLoadingContext, PluginLoadingResult> {
    val initContext = initContext ?: buildInitContext()
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val result = PluginLoadingResult()
    val pluginList = DiscoveredPluginsList(pluginDescriptorLoader(loadingContext), PluginsSourceContext.Custom)
    loadingContext.use {
      @Suppress("RAW_RUN_BLOCKING") //it's used in tests where the Application isn't available
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
      coreLoader = customCoreLoader ?: UrlClassLoader.build().get(),
      parentActivity = null,
    ).pluginSet
  }
}