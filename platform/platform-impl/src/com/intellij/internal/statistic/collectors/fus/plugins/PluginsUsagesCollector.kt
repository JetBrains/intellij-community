// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.ide.plugins.*
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.service.fus.collectors.AllowedDuringStartupCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.extensions.PluginId

class PluginsUsagesCollector : ApplicationUsagesCollector(), AllowedDuringStartupCollector {
  companion object {
    private val GROUP = EventLogGroup("plugins", 7)
    private val DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin", EventFields.PluginInfo)
    private val ENABLED_NOT_BUNDLED_PLUGIN = GROUP.registerEvent("enabled.not.bundled.plugin", EventFields.PluginInfo)
    private val PER_PROJECT_ENABLED = GROUP.registerEvent("per.project.enabled", EventFields.Count)
    private val PER_PROJECT_DISABLED = GROUP.registerEvent("per.project.disabled", EventFields.Count)
    private val UNSAFE_PLUGIN = GROUP.registerEvent("unsafe.plugin",
                                                    EventFields.String("unsafe_id", emptyList()), EventFields.Boolean("enabled"))
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics() = HashSet<MetricEvent>().apply {
    addAll(getDisabledPlugins())
    addAll(getEnabledNonBundledPlugins())
    addAll(getPerProjectPlugins(PER_PROJECT_ENABLED, ProjectPluginTracker::enabledPluginsIds))
    addAll(getPerProjectPlugins(PER_PROJECT_DISABLED, ProjectPluginTracker::disabledPluginsIds))
    addAll(getNotBundledPlugins())
  }

  private fun getDisabledPlugins() = DisabledPluginsState
    .disabledPlugins()
    .map {
      DISABLED_PLUGIN.metric(getPluginInfoById(it))
    }.toSet()

  private fun getEnabledNonBundledPlugins() = PluginManagerCore
    .getLoadedPlugins()
    .filter { it.isEnabled && !it.isBundled && !PluginManagerCore.isUpdatedBundledPlugin(it) }
    .map { getPluginInfoByDescriptor(it) }
    .map(ENABLED_NOT_BUNDLED_PLUGIN::metric)
    .toSet()

  private fun getPerProjectPlugins(
    eventId: EventId1<Int>,
    countProducer: (ProjectPluginTracker) -> Set<PluginId>
  ): Set<MetricEvent> {
    return when (val pluginEnabler = PluginEnabler.getInstance()) {
      is DynamicPluginEnabler ->
        pluginEnabler.trackers.values
          .map { countProducer(it) }
          .filter { it.isNotEmpty() }
          .map { eventId.metric(it.size) }
          .toSet()
      else ->
        emptySet()
    }
  }

  private fun getNotBundledPlugins() = PluginManagerCore
    .getPlugins().asSequence()
    .filter { !it.isBundled && !getPluginInfoByDescriptor(it).isSafeToReport() }
    // This will be validated by list of plugin ids from server
    // and ONLY provided ids will be reported
    .map { UNSAFE_PLUGIN.metric(it.pluginId.idString, it.isEnabled) }
    .toSet()
}