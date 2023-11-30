// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.ide.plugins.DisabledPluginsState.Companion.getDisabledIds
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById

internal class PluginsUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("plugins", 9)
  private val DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin", EventFields.PluginInfo)
  private val ENABLED_NOT_BUNDLED_PLUGIN = GROUP.registerEvent("enabled.not.bundled.plugin", EventFields.PluginInfo)
  private val UNSAFE_PLUGIN = GROUP.registerEvent("unsafe.plugin",
                                                  EventFields.String("unsafe_id", emptyList()), EventFields.Boolean("enabled"))

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics() = HashSet<MetricEvent>().apply {
    addAll(getDisabledPlugins())
    addAll(getEnabledNonBundledPlugins())
    addAll(getNotBundledPlugins())
  }

  private fun getDisabledPlugins() = getDisabledIds()
    .map {
      DISABLED_PLUGIN.metric(getPluginInfoById(it))
    }.toSet()

  private fun getEnabledNonBundledPlugins(): Set<MetricEvent> {
    return PluginManagerCore
      .loadedPlugins
      .filter { it.isEnabled && !it.isBundled }
      .map { getPluginInfoByDescriptor(it) }
      .map(ENABLED_NOT_BUNDLED_PLUGIN::metric)
      .toSet()
  }

  private fun getNotBundledPlugins(): Set<MetricEvent> {
    return PluginManagerCore.plugins.asSequence()
      .filter { !it.isBundled && !getPluginInfoByDescriptor(it).isSafeToReport() }
      // This will be validated by list of plugin ids from server
      // and ONLY provided ids will be reported
      .map { UNSAFE_PLUGIN.metric(it.pluginId.idString, it.isEnabled) }
      .toSet()
  }
}