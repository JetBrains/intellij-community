// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfo(clazz: Class<*>): PluginInfo {
  return getPluginInfo(clazz.name)
}

fun getPluginInfo(className: String): PluginInfo {
  if (className.startsWith("java.") || className.startsWith("javax.") ||
      className.startsWith("kotlin.") || className.startsWith("groovy.")) {
    return platformPlugin
  }

  val pluginId = PluginManager.getPluginOrPlatformByClassName(className) ?: return unknownPlugin
  return getPluginInfoByDescriptor(PluginManager.getInstance().findEnabledPlugin(pluginId) ?: return unknownPlugin)
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
  return getPluginInfoByDescriptor(PluginManagerCore.getPlugin(pluginId) ?: return unknownPlugin)
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
  if (PluginManager.isDevelopedByJetBrains(plugin)) {
    return if (plugin.isBundled) PluginInfo(PluginType.JB_BUNDLED, id) else PluginInfo(PluginType.JB_NOT_BUNDLED, id)
  }

  // only plugins installed from some repository (not bundled and not provided via classpath in development IDE instance -
  // they are also considered bundled) would be reported
  val listed = !plugin.isBundled && isSafeToReport(id)
  return if (listed) {
    PluginInfo(PluginType.LISTED, id)
  }
  else {
    notListedPlugin
  }
}

enum class PluginType {
  PLATFORM, JB_BUNDLED, JB_NOT_BUNDLED, LISTED, NOT_LISTED, UNKNOWN;

  private fun isPlatformOrJBBundled(): Boolean {
    return this == PLATFORM || this == JB_BUNDLED
  }

  fun isDevelopedByJetBrains(): Boolean {
    return isPlatformOrJBBundled() || this == JB_NOT_BUNDLED
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

class PluginInfo(val type: PluginType, val id: String?) {

  /**
   * @return true if code is from IntelliJ platform or JB plugin.
   */
  fun isDevelopedByJetBrains(): Boolean {
    return type.isDevelopedByJetBrains()
  }

  /**
   * @return true if code is from IntelliJ platform, JB plugin or plugin from JB plugin repository.
   */
  fun isSafeToReport(): Boolean {
    return type.isSafeToReport()
  }
}

val platformPlugin: PluginInfo = PluginInfo(PluginType.PLATFORM, null)
val unknownPlugin: PluginInfo = PluginInfo(PluginType.UNKNOWN, null)
val notListedPlugin: PluginInfo = PluginInfo(PluginType.NOT_LISTED, null)