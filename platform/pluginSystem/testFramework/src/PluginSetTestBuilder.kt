// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginSystem.testFramework

import com.intellij.ide.plugins.DiscoveredPluginsList
import com.intellij.ide.plugins.PluginDescriptorLoadingContext
import com.intellij.ide.plugins.PluginInitContextFactory
import com.intellij.ide.plugins.PluginInitializationContext
import com.intellij.ide.plugins.PluginLoadingErrorReportingPolicy
import com.intellij.ide.plugins.PluginMainDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerState
import com.intellij.ide.plugins.PluginSet
import com.intellij.ide.plugins.PluginsDiscoveryResult
import com.intellij.ide.plugins.PluginsSourceContext
import com.intellij.ide.plugins.loadDescriptorFromFileOrDir
import com.intellij.ide.plugins.withCustomFactoryInUnitTests
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.platform.runtime.product.ProductMode
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.lang.UrlClassLoader
import java.nio.file.Path

class PluginSetTestBuilder private constructor(
  private val pluginDescriptorLoader: (loadingContext: PluginDescriptorLoadingContext) -> List<PluginMainDescriptor>,
) {
  private var disabledPluginIds = mutableSetOf<PluginId>()
  private var expiredPluginIds = mutableSetOf<PluginId>()
  private var brokenPlugins = mutableMapOf<PluginId, MutableSet<String?>>()
  private var productBuildNumber = PluginManagerCore.buildNumber
  private var customCoreLoader: UrlClassLoader? = null
  private var productMode: ProductMode = ProductMode.MONOLITH
  private var explicitPluginSubsetToLoad: Set<PluginId>? = null

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

  fun withExplicitPluginSubsetToLoad(pluginsToLoad: Set<PluginId>): PluginSetTestBuilder = apply {
    this.explicitPluginSubsetToLoad = pluginsToLoad
  }

  var buildNumber: String
    get() = productBuildNumber.toString()
    set(value) {
      productBuildNumber = BuildNumber.fromString(value)!!
    }

  fun buildInitContext(): PluginInitializationContext {
    return object : PseudoProductTestPluginInitContext() {
      override val expiredPlugins: Set<PluginId> = this@PluginSetTestBuilder.expiredPluginIds
      override val productBuildNumber: BuildNumber = this@PluginSetTestBuilder.productBuildNumber
      override fun isPluginDisabled(id: PluginId): Boolean = id in disabledPluginIds
      override fun isPluginBroken(id: PluginId, version: String?): Boolean {
        brokenPlugins[id]?.let { return version in it }
        return false
      }
      override val explicitPluginSubsetToLoad: Set<PluginId>? = this@PluginSetTestBuilder.explicitPluginSubsetToLoad
      override val currentProductModeId: String = productMode.id
    }
  }

  fun discoverPlugins(): Pair<PluginDescriptorLoadingContext, PluginsDiscoveryResult> {
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val pluginList = DiscoveredPluginsList(pluginDescriptorLoader(loadingContext), PluginsSourceContext.Custom)
    return loadingContext to PluginsDiscoveryResult.build(listOf(pluginList))
  }

  fun buildState(configureClassLoaders: Boolean = true): PluginManagerState {
    val initContext = buildInitContext()
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val pluginList = PluginInitContextFactory.withCustomFactoryInUnitTests(TestPluginInitContextFactory(initContext)) { // FIXME this should not exist
      DiscoveredPluginsList(pluginDescriptorLoader(loadingContext), PluginsSourceContext.Custom)
    }
    val discoveredPlugins = PluginsDiscoveryResult.build(listOf(pluginList))
    return PluginManagerCore.initializePlugins(
      descriptorLoadingErrors = loadingContext.copyDescriptorLoadingErrors(),
      initContext = initContext,
      discoveredPlugins = discoveredPlugins,
      coreLoader = customCoreLoader ?: UrlClassLoader.build().get(),
      parentActivity = null,
      configureClassLoaders = configureClassLoaders,
      reportingPolicy = PluginLoadingErrorReportingPolicy.TEST,
    )
  }

  fun build(): PluginSet = buildState().pluginSet
}