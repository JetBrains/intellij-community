// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.platform.ide.bootstrap.ZipFilePoolImpl
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

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
    return PluginInitializationContext.build(
      disabledPlugins = disabledPluginIds.toSet(),
      expiredPlugins = expiredPluginIds.toSet(),
      brokenPluginVersions = brokenPlugins.mapValues { it.value.toSet() }.toMap(),
      getProductBuildNumber = { buildNumber },
    )
  }

  fun buildLoadingResult(initContext: PluginInitializationContext? = null): PluginLoadingResult {
    val initContext = initContext ?: buildInitContext()
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val result = PluginLoadingResult(checkModuleDependencies = false)
    // constant order in tests
    val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
    loadingContext.use {
      runBlocking {
        result.initAndAddAll(
          descriptors = paths.asSequence().mapNotNull { path -> loadDescriptor(path, loadingContext, ZipFilePoolImpl()) },
          overrideUseIfCompatible = false,
          initContext = initContext,
        )
      }
    }
    return result
  }

  fun build(): PluginSet {
    val initContext = buildInitContext()
    val loadingContext = PluginDescriptorLoadingContext(getBuildNumberForDefaultDescriptorVersion = { productBuildNumber })
    val loadingResult = buildLoadingResult(initContext)
    return PluginManagerCore.initializePlugins(
      loadingContext = loadingContext,
      initContext = initContext,
      loadingResult = loadingResult,
      coreLoader = UrlClassLoader.build().get(),
      checkEssentialPlugins = false,
      getEssentialPlugins = ::emptyList,
      parentActivity = null,
    ).pluginSet
  }
}