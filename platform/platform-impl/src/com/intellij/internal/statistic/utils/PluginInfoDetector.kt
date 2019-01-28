// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.utils

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginManagerMain
import com.intellij.openapi.extensions.PluginId

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfo(clazz: Class<*>): PluginInfo {
  val pluginId = PluginManagerCore.getPluginByClassName(clazz.name) ?: return platformPlugin
  return getPluginInfoById(pluginId)
}

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfoById(pluginId: PluginId?): PluginInfo {
  if (pluginId == null) return unknownPlugin

  return getPluginInfoByDescriptor(PluginManager.getPlugin(pluginId))
}

/**
 * Returns if this code is coming from IntelliJ platform, a plugin created by JetBrains (bundled or not) or from official repository,
 * so API from it may be reported
 */
fun getPluginInfoByDescriptor(plugin: IdeaPluginDescriptor?): PluginInfo {
  if (plugin == null) return unknownPlugin

  val id = plugin.pluginId.idString
  if (PluginManagerMain.isDevelopedByJetBrains(plugin)) {
    return if (plugin.isBundled) {
      PluginInfo(PluginType.JB_BUNDLED, id)
    }
    else {
      PluginInfo(PluginType.JB_NOT_BUNDLED, id)
    }
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

  fun isPlatformOrJBBundled(): Boolean {
    return this == PLATFORM || this == JB_BUNDLED
  }

  fun isDevelopedByJetBrains(): Boolean {
    return isPlatformOrJBBundled() || this == JB_NOT_BUNDLED
  }

  fun isSafeToReport(): Boolean {
    return isDevelopedByJetBrains() || this == LISTED
  }
}

class PluginInfo(val type: PluginType, val id: String?) {

  fun isDevelopedByJetBrains(): Boolean {
    return type.isDevelopedByJetBrains()
  }

  fun isSafeToReport(): Boolean {
    return type.isSafeToReport()
  }
}

val platformPlugin: PluginInfo = PluginInfo(PluginType.PLATFORM, null)
val unknownPlugin: PluginInfo = PluginInfo(PluginType.UNKNOWN, null)
val notListedPlugin: PluginInfo = PluginInfo(PluginType.NOT_LISTED, null)