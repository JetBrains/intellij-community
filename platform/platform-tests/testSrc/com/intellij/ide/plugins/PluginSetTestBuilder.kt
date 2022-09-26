// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.BuildNumber
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.lang.UrlClassLoader
import kotlinx.coroutines.runBlocking
import java.nio.file.Path

class PluginSetTestBuilder(private val path: Path) {

  private var disabledPluginIds = mutableSetOf<String>()
  private var expiredPluginIds = mutableSetOf<String>()
  private var enabledOnDemandPluginIds = mutableSetOf<String>()
  private var productBuildNumber = PluginManagerCore.getBuildNumber()

  private var context: DescriptorListLoadingContext? = null
  private var result: PluginLoadingResult? = null

  fun withDisabledPlugins(vararg disabledPluginIds: String) = apply {
    this.disabledPluginIds += disabledPluginIds
  }

  fun withExpiredPlugins(vararg expiredPluginIds: String) = apply {
    this.expiredPluginIds += expiredPluginIds
  }

  fun withEnabledOnDemandPlugins(vararg enabledOnDemandPluginIds: String) = apply {
    this.enabledOnDemandPluginIds += enabledOnDemandPluginIds
  }

  fun withProductBuildNumber(productBuildNumber: BuildNumber) = apply {
    this.productBuildNumber = productBuildNumber
  }

  fun withLoadingContext() = apply {
    context = DescriptorListLoadingContext(
      disabledPlugins = disabledPluginIds.toPluginIds(),
      expiredPlugins = expiredPluginIds.toPluginIds(),
      enabledOnDemandPlugins = enabledOnDemandPluginIds.toPluginIds(),
      brokenPluginVersions = emptyMap(),
      productBuildNumber = { productBuildNumber },
    )
  }

  @Suppress("MemberVisibilityCanBePrivate")
  val loadingContext: DescriptorListLoadingContext
    get() = context ?: withLoadingContext().context!!

  fun withLoadingResult() = apply {
    result = PluginLoadingResult(checkModuleDependencies = false)
    // constant order in tests
    val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
    loadingContext.use {
      runBlocking {
        result!!.addAll(
          descriptors = paths.map { loadDescriptor(it, loadingContext) },
          overrideUseIfCompatible = false,
          productBuildNumber = loadingContext.productBuildNumber(),
        )
      }
    }
  }

  val loadingResult: PluginLoadingResult
    get() = result ?: withLoadingResult().result!!

  fun build(): PluginSet {
    return PluginManagerCore.initializePlugins(
      /* context = */ loadingContext,
      /* loadingResult = */ loadingResult,
      /* coreLoader = */ UrlClassLoader.build().get(),
      /* checkEssentialPlugins = */ false,
      /* parentActivity = */ null,
    ).pluginSet
  }
}