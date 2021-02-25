// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.collectors.fus.plugins

import com.intellij.ide.plugins.*
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.internal.statistic.utils.getPluginInfoById
import com.intellij.openapi.extensions.PluginId
import java.util.*


class PluginsUsagesCollector : ApplicationUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("plugins", 5)
    private val DISABLED_PLUGIN = GROUP.registerEvent("disabled.plugin", EventFields.PluginInfo)
    private val ENABLED_NOT_BUNDLED_PLUGIN = GROUP.registerEvent("enabled.not.bundled.plugin", EventFields.PluginInfo)
    private val PER_PROJECT_ENABLED =  GROUP.registerEvent("per.project.enabled", EventFields.Count)
    private val PER_PROJECT_DISABLED = GROUP.registerEvent("per.project.disabled", EventFields.Count)
  }

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics() = HashSet<MetricEvent>().apply {
    addAll(getDisabledPlugins())
    addAll(getEnabledNonBundledPlugins())
    addAll(getPerProjectPlugins(PER_PROJECT_ENABLED, ProjectPluginTracker::enabledPluginsIds))
    addAll(getPerProjectPlugins(PER_PROJECT_DISABLED, ProjectPluginTracker::disabledPluginsIds))
  }

  private fun getDisabledPlugins() = DisabledPluginsState
    .disabledPlugins()
    .map {
      DISABLED_PLUGIN.metric(getPluginInfoById(it))
    }.toSet()

  private fun getEnabledNonBundledPlugins() = PluginManagerCore
    .getLoadedPlugins()
    .filter { it.isEnabled && !it.isBundled }
    .map { getPluginInfoByDescriptor(it) }
    .map(ENABLED_NOT_BUNDLED_PLUGIN::metric)
    .toSet()

  private fun getPerProjectPlugins(
    eventId: EventId1<Int>,
    countProducer: (ProjectPluginTracker) -> Set<PluginId>
  ) = ProjectPluginTrackerManager
    .instance
    .trackers
    .values
    .map { countProducer(it) }
    .filter { it.isNotEmpty() }
    .map { eventId.metric(it.size) }
    .toSet()
}