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
  private var disabledPluginIds = mutableSetOf<String>()
  private var expiredPluginIds = mutableSetOf<String>()
  private var productBuildNumber = PluginManagerCore.buildNumber
  private var brokenPlugins = mutableMapOf<PluginId, MutableSet<String?>>()
  private var essentialPlugins = mutableListOf<PluginId>()

  private var context: DescriptorListLoadingContext? = null
  private var result: PluginLoadingResult? = null

  fun withDisabledPlugins(vararg disabledPluginIds: String) = apply {
    this.disabledPluginIds += disabledPluginIds
  }

  fun withExpiredPlugins(vararg expiredPluginIds: String) = apply {
    this.expiredPluginIds += expiredPluginIds
  }

  fun withBrokenPlugin(pluginId: String, vararg versions: String?) = apply {
    brokenPlugins.computeIfAbsent(PluginId.getId(pluginId), { mutableSetOf() }).addAll(versions)
  }

  fun withEssentialPlugins(vararg ids: String) = apply {
    essentialPlugins.addAll(ids.map(PluginId::getId))
  }

  fun withProductBuildNumber(productBuildNumber: BuildNumber) = apply {
    this.productBuildNumber = productBuildNumber
  }

  var buildNumber: String
    get() = productBuildNumber.toString()
    set(value) {
      productBuildNumber = BuildNumber.fromString(value)!!
    }

  fun withLoadingContext(): PluginSetTestBuilder {
    return apply {
      context = DescriptorListLoadingContext(
        customDisabledPlugins = PluginManagerCore.toPluginIds(disabledPluginIds),
        customExpiredPlugins = PluginManagerCore.toPluginIds(expiredPluginIds),
        customBrokenPluginVersions = brokenPlugins,
        customEssentialPlugins = essentialPlugins,
        productBuildNumber = { productBuildNumber },
      )
    }
  }

  @Suppress("MemberVisibilityCanBePrivate")
  val loadingContext: DescriptorListLoadingContext
    get() = context ?: withLoadingContext().context!!

  fun withLoadingResult(): PluginSetTestBuilder {
    return apply {
      result = PluginLoadingResult(checkModuleDependencies = false)
      // constant order in tests
      val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
      loadingContext.use {
        runBlocking {
          result!!.addAll(
            descriptors = paths.asSequence().mapNotNull { loadDescriptor(it, loadingContext, ZipFilePoolImpl()) },
            overrideUseIfCompatible = false,
            productBuildNumber = loadingContext.productBuildNumber(),
          )
        }
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