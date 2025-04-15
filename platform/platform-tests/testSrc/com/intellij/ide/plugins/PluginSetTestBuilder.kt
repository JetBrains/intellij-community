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
  private var essentialPlugins = mutableListOf<PluginId>()
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

  fun buildLoadingContext(): DescriptorListLoadingContext {
    // copy just in case
    val buildNumber = productBuildNumber
    return DescriptorListLoadingContext(
      customDisabledPlugins = disabledPluginIds.toSet(),
      customExpiredPlugins = expiredPluginIds.toSet(),
      customBrokenPluginVersions = brokenPlugins.mapValues { it.value.toSet() }.toMap(),
      customEssentialPlugins = essentialPlugins.toList(),
      productBuildNumber = { buildNumber },
    )
  }

  fun buildLoadingResult(context: DescriptorListLoadingContext? = null): PluginLoadingResult {
    val context = context ?: buildLoadingContext()
    val result = PluginLoadingResult(checkModuleDependencies = false)
    // constant order in tests
    val paths: List<Path> = path.directoryStreamIfExists { it.sorted() }!!
    context.use {
      runBlocking {
        result.initAndAddAll(
          descriptors = paths.asSequence().mapNotNull { path -> loadDescriptor(path, context, ZipFilePoolImpl()) },
          overrideUseIfCompatible = false,
          productBuildNumber = context.productBuildNumber(),
          isPluginDisabled = context::isPluginDisabled,
          isPluginBroken = context::isPluginBroken,
        )
      }
    }
    return result
  }

  fun build(): PluginSet {
    val context = buildLoadingContext()
    val loadingResult = buildLoadingResult(context)
    return PluginManagerCore.initializePlugins(
      /* context = */ context,
      /* loadingResult = */ loadingResult,
      /* coreLoader = */ UrlClassLoader.build().get(),
      /* checkEssentialPlugins = */ false,
      /* parentActivity = */ null,
    ).pluginSet
  }
}