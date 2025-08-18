// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.PluginInfoProvider
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginUtils
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.internal.statistic.FeaturedPluginsInfoProvider
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.TimeoutCachedValue
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

/**
 * Returns if this code is coming from IntelliJ Platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfo(aClass: Class<*>): PluginInfo {
  val classLoader = aClass.classLoader
  return when {
    classLoader is PluginAwareClassLoader -> getPluginInfoByDescriptor(classLoader.pluginDescriptor)
    PluginManagerCore.isRunningFromSources() -> builtFromSources
    else -> getPluginInfo(aClass.name)
  }
}

@ApiStatus.Internal
fun hasStandardExceptionPrefix(className: String): Boolean =
  className.startsWith("java.") || className.startsWith("javax.") ||
  className.startsWith("sun.") || className.startsWith("com.sun.") || className.startsWith("jdk.") ||
  className.startsWith("kotlin.") || className.startsWith("kotlinx.") ||
  className.startsWith("groovy.")

fun getPluginInfo(className: String): PluginInfo {
  if (hasStandardExceptionPrefix(className)) {
    return jvmCore
  }

  val plugin = PluginUtils.getPluginDescriptorOrPlatformByClassName(className) ?: return unknownPlugin
  return getPluginInfoByDescriptor(plugin)
}

/**
 * Returns if this code is coming from IntelliJ Platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported.
 *
 * Use only if you don't have [PluginDescriptor].
 */
fun getPluginInfoById(pluginId: PluginId?): PluginInfo {
  if (pluginId == null) {
    return unknownPlugin
  }
  val plugin = PluginManagerCore.getPlugin(pluginId)
  if (plugin == null) {
    // we can't load plugin descriptor for a not installed plugin, but we can check if it's from JetBrains Marketplace
    return if (isPluginFromOfficialJbPluginRepo(pluginId)) PluginInfo(PluginType.LISTED, pluginId.idString, null) else unknownPlugin
  }
  return getPluginInfoByDescriptor(plugin)
}

/**
 * Returns if this code is coming from IntelliJ Platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfoByDescriptor(plugin: PluginDescriptor): PluginInfo = getPluginInfoByDescriptorWithFeaturedPlugins(plugin, null)

/**
 * Use 'getPluginInfoByDescriptor' method to detect plugin info instead of this one unless you want to report installed featured plugins.
 *
 * Featured plugins are installed during the first IDE start when there's no cached list of Marketplace plugins,
 * but we verify that all featured plugins exist on Marketplace and are compatible with IDE version, so they are safe to report.
 *
 * @see getPluginInfoByDescriptor
 * @see com.intellij.ide.customize.PluginGroups
 */
fun getPluginInfoByDescriptorWithFeaturedPlugins(plugin: PluginDescriptor, featuredPlugins: FeaturedPluginsInfoProvider?): PluginInfo {
  if (PluginManagerCore.CORE_ID == plugin.pluginId) {
    return platformPlugin
  }

  val id = plugin.pluginId.idString
  val version = plugin.version
  if (PluginManagerCore.isDevelopedByJetBrains(plugin)) {
    val pluginType = when {
      plugin.isBundled -> PluginType.JB_BUNDLED
      PluginManagerCore.isUpdatedBundledPlugin(plugin) -> PluginType.JB_UPDATED_BUNDLED
      else -> PluginType.JB_NOT_BUNDLED
    }
    return PluginInfo(pluginType, id, version)
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val listed = !plugin.isBundled && !PluginManagerCore.isUpdatedBundledPlugin(plugin) && isSafeToReportFrom(plugin, featuredPlugins)
  return if (listed) PluginInfo(PluginType.LISTED, id, version) else notListedPlugin
}

enum class PluginType {
  /**
   * JVM core libraries
   */
  JVM_CORE,

  /**
   * IntelliJ Platform
   */
  PLATFORM,

  /**
   * Plugin implemented by JetBrains, bundled with a product
   */
  JB_BUNDLED,

  /**
   * Plugin implemented by JetBrains but not bundled with a product
   */
  JB_NOT_BUNDLED,

  /**
   * Third-party plugin, available on Marketplace
   */
  LISTED,

  /**
   * Third-party plugin, installed from disk or custom repository
   */
  NOT_LISTED,

  /**
   * Cannot detect plugin type
   */
  UNKNOWN,

  /**
   * Cannot detect plugin type because IDE was built from sources
   */
  FROM_SOURCES,

  /**
   * Plugin implemented by JetBrains, bundled with a product but the version is different from the one which was bundled
   */
  JB_UPDATED_BUNDLED;

  /**
   * @return true if code is from IntelliJ Platform or JVM
   */
  fun isPlatformOrJvm(): Boolean {
    return this == JVM_CORE || this == PLATFORM
  }

  /**
   * @return true if code is from IntelliJ Platform or JetBrains plugin.
   */
  fun isDevelopedByJetBrains(): Boolean {
    return this == JB_BUNDLED || this == FROM_SOURCES || this == JB_UPDATED_BUNDLED || this == JB_NOT_BUNDLED || isPlatformOrJvm()
  }

  /**
   * @return true if code is from IntelliJ Platform, JetBrains plugin or plugin from JetBrains Marketplace.
   */
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

private const val tbePluginId = "org.jetbrains.toolbox-enterprise-client"
private const val aeExperimentsPluginId = "com.jetbrains.ae.experiments"
private const val aeDatabasePluginId = "com.jetbrains.ae.database"

private val allowedPlugins = setOf(tbePluginId, aeExperimentsPluginId, aeDatabasePluginId)

data class PluginInfo(val type: PluginType, val id: String?, val version: String?) {
  /**
   * @return true if code is from IntelliJ Platform or JetBrains plugin.
   */
  fun isDevelopedByJetBrains() = type.isDevelopedByJetBrains()

  /**
   * @return true if code is from IntelliJ Platform, JetBrains plugin or plugin from JetBrains Marketplace.
   */
  fun isSafeToReport() = type.isSafeToReport()

  fun isAllowedToInjectIntoFUS(): Boolean {
    return (type.isDevelopedByJetBrains() && allowedPlugins.contains(id)) ||
           (PluginManagerCore.isUnitTestMode && (type == PluginType.PLATFORM || type == PluginType.FROM_SOURCES))
  }
}

private val jvmCore: PluginInfo = PluginInfo(PluginType.JVM_CORE, null, null)
val platformPlugin: PluginInfo = PluginInfo(PluginType.PLATFORM, null, null)
val unknownPlugin: PluginInfo = PluginInfo(PluginType.UNKNOWN, null, null)
private val notListedPlugin = PluginInfo(PluginType.NOT_LISTED, null, null)

// Mock plugin info used when we can't detect plugin by class loader because IDE is built from sources
private val builtFromSources = PluginInfo(PluginType.FROM_SOURCES, null, null)

private val pluginIdsFromOfficialJbPluginRepo: Supplier<Set<PluginId>> = TimeoutCachedValue(1, TimeUnit.HOURS) {
  // before loading default repository plugins lets check it's not changed, and is really official JetBrains repository
  val infoProvider = PluginInfoProvider.getInstance()
  infoProvider.loadCachedPlugins()
  ?: emptySet<PluginId?>().also { infoProvider.loadPlugins(null) } // schedule plugins loading, report nothing until repo plugins loaded
}

/**
 * Checks this plugin is created by JetBrains or from official repository, so API from it may be reported
 */
private fun isSafeToReportFrom(descriptor: PluginDescriptor, featuredPlugins: FeaturedPluginsInfoProvider?): Boolean {
  return when {
    PluginManagerCore.isDevelopedByJetBrains(descriptor) -> true
    descriptor.isBundled -> false // bundled, but not from JetBrains, so, some custom unknown plugin
    else -> isPluginFromOfficialJbPluginRepo(descriptor.pluginId, featuredPlugins)
    // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance - they are also considered bundled) would be reported
  }
}

private fun isPluginFromOfficialJbPluginRepo(pluginId: PluginId?, featuredPlugins: FeaturedPluginsInfoProvider? = null): Boolean {
  // not official JetBrains repository - is used, so, not safe to report
  if (ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()) {
    if (featuredPlugins != null && isClassFromCoreOrJetBrainsPlugin(featuredPlugins.javaClass) &&
        featuredPlugins.getFeaturedPluginsFromMarketplace().contains(pluginId)) {
      return true
    }
    return pluginIdsFromOfficialJbPluginRepo.get().contains(pluginId)
  }
  return false
}

private fun isClassFromCoreOrJetBrainsPlugin(clazz: Class<*>): Boolean {
  val loader = clazz.classLoader
  if (loader is PluginAwareClassLoader) {
    return isCoreOrJetBrainsPlugin((loader as PluginAwareClassLoader).pluginDescriptor)
  }
  val descriptor = PluginUtils.getPluginDescriptorOrPlatformByClassName(clazz.name)
  return descriptor != null && isCoreOrJetBrainsPlugin(descriptor)
}

private fun isCoreOrJetBrainsPlugin(descriptor: PluginDescriptor): Boolean {
  return PluginManagerCore.CORE_ID == descriptor.pluginId || PluginManagerCore.isDevelopedByJetBrains(descriptor)
}
