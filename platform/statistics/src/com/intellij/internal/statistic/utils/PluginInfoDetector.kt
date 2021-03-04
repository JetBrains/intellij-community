// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Getter
import com.intellij.openapi.util.TimeoutCachedValue
import java.util.concurrent.TimeUnit

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfo(clazz: Class<*>): PluginInfo {
  val classLoader = clazz.classLoader
  return when {
    classLoader is PluginAwareClassLoader -> {
      getPluginInfoByDescriptor(classLoader.pluginDescriptor)
    }
    PluginManagerCore.isRunningFromSources() && !PluginManagerCore.isUnitTestMode -> {
      builtFromSources
    }
    else -> {
      getPluginInfo(clazz.name)
    }
  }
}

fun getPluginInfo(className: String): PluginInfo {
  if (className.startsWith("java.") || className.startsWith("javax.") ||
      className.startsWith("kotlin.") || className.startsWith("groovy.")) {
    return platformPlugin
  }

  val plugin = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(className) ?: return unknownPlugin
  return getPluginInfoByDescriptor(plugin)
}

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported.
 *
 * Use only if you don't have [PluginDescriptor].
 */
fun getPluginInfoById(pluginId: PluginId?): PluginInfo {
  if (pluginId == null) {
    return unknownPlugin
  }
  val plugin = PluginManagerCore.getPlugin(pluginId)
  @Suppress("FoldInitializerAndIfToElvis")
  if (plugin == null) {
    // we can't load plugin descriptor for a not installed plugin but we can check if it's from JB repo
    return if (isPluginFromOfficialJbPluginRepo(pluginId)) PluginInfo(PluginType.LISTED, pluginId.idString, null) else unknownPlugin
  }
  return getPluginInfoByDescriptor(plugin)
}

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfoByDescriptor(plugin: PluginDescriptor): PluginInfo {
  if (PluginManagerCore.CORE_ID == plugin.pluginId) {
    return platformPlugin
  }

  val id = plugin.pluginId.idString
  val version = plugin.version
  if (PluginManager.getInstance().isDevelopedByJetBrains(plugin)) {
    val pluginType = when {
      plugin.isBundled -> PluginType.JB_BUNDLED
      PluginManagerCore.isUpdatedBundledPlugin(plugin) -> PluginType.JB_UPDATED_BUNDLED
      else -> PluginType.JB_NOT_BUNDLED
    }
    return PluginInfo(pluginType, id, version)
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val listed = !plugin.isBundled && !PluginManagerCore.isUpdatedBundledPlugin(plugin) && isSafeToReportFrom(plugin)
  return if (listed) PluginInfo(PluginType.LISTED, id, version) else notListedPlugin
}

enum class PluginType {
  PLATFORM, JB_BUNDLED, JB_NOT_BUNDLED, LISTED, NOT_LISTED, UNKNOWN, FROM_SOURCES, JB_UPDATED_BUNDLED;

  fun isPlatformOrJetBrainsBundled(): Boolean {
    return this == PLATFORM || this == JB_BUNDLED || this == FROM_SOURCES || this == JB_UPDATED_BUNDLED
  }

  fun isDevelopedByJetBrains(): Boolean {
    return isPlatformOrJetBrainsBundled() || this == JB_NOT_BUNDLED
  }

  fun isSafeToReport(): Boolean {
    return isDevelopedByJetBrains() || this == LISTED
  }
}

fun findPluginTypeByValue(value: String): PluginType? {
  for (type in PluginType.values()) {
    if (type.name == value) {
      return type
    }
  }
  return null
}

data class PluginInfo(val type: PluginType, val id: String?, val version: String?) {
  /**
   * @return true if code is from IntelliJ platform or JB plugin.
   */
  fun isDevelopedByJetBrains() = type.isDevelopedByJetBrains()

  /**
   * @return true if code is from IntelliJ platform, JB plugin or plugin from JB plugin repository.
   */
  fun isSafeToReport() = type.isSafeToReport()
}

val platformPlugin: PluginInfo = PluginInfo(PluginType.PLATFORM, null, null)
val unknownPlugin: PluginInfo = PluginInfo(PluginType.UNKNOWN, null, null)
private val notListedPlugin = PluginInfo(PluginType.NOT_LISTED, null, null)

// Mock plugin info used when we can't detect plugin by class loader because IDE is built from sources
val builtFromSources: PluginInfo = PluginInfo(PluginType.FROM_SOURCES, null, null)

private val pluginIdsFromOfficialJbPluginRepo: Getter<Set<PluginId>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
  // before loading default repository plugins lets check it's not changed, and is really official JetBrains repository
  val infoProvider = PluginInfoProvider.getInstance()
  infoProvider.loadCachedPlugins()
  ?: emptySet<PluginId?>().also { infoProvider.loadPlugins(null) } // schedule plugins loading, report nothing until repo plugins loaded
}

/**
 * Checks this plugin is created by JetBrains or from official repository, so API from it may be reported
 */
private fun isSafeToReportFrom(descriptor: PluginDescriptor): Boolean {
  return when {
    PluginManager.getInstance().isDevelopedByJetBrains(descriptor) -> true
    descriptor.isBundled -> false // bundled, but not from JetBrains, so, some custom unknown plugin
    else -> descriptor.pluginId?.let { isPluginFromOfficialJbPluginRepo(it) } ?: false
    // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance - they are also considered bundled) would be reported
  }
}

private fun isPluginFromOfficialJbPluginRepo(pluginId: PluginId?): Boolean {
  // not official JetBrains repository - is used, so, not safe to report
  return if (ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository())
    pluginIdsFromOfficialJbPluginRepo.get().contains(pluginId)
  else
    false
}