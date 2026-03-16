// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields.AutomatedPluginVersion
import com.intellij.internal.statistic.eventLog.events.EventIdName
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector
import com.intellij.internal.statistic.utils.PluginInfo
import com.intellij.internal.statistic.utils.getPluginInfo
import org.jetbrains.annotations.ApiStatus

/**
 * A class extending `EventLogGroup` that enables automatically adding plugin versions to all events belonging to the group.
 *
 * The event data field will be explicitly added to metadata. See [com.intellij.internal.statistic.eventLog.events.EventId]
 * for appending logic.
 *
 * @param id Unique identifier for the event group.
 * @param version Version of the event group.
 * @param recorder Name of the event recorder.
 * @param pluginLoadedClass The class used to get the plugin classloader. This is required to get the PluginInfo.
 */
@ApiStatus.Internal
class EventLogGroupPluginAware<T>(
  @EventIdName id: String,
  version: Int,
  recorder: String,
  pluginLoadedClass: Class<T>,
) : EventLogGroup(id, version, recorder, listOf(createPluginVersionField(pluginLoadedClass))) {
  companion object {
    inline fun <reified T : FeatureUsagesCollector> create(
      id: String,
      version: Int,
      recorder: String,
    ): EventLogGroupPluginAware<T> = EventLogGroupPluginAware(id, version, recorder, T::class.java)

    private fun <T> createPluginVersionField(collectorClass: Class<T>): Pair<EventField<*>, FeatureUsageData.() -> Unit> {
      val dataSupplier: (fuData : FeatureUsageData) -> Unit = object : (FeatureUsageData) -> Unit {
        val pluginInfo : PluginInfo by lazy { getPluginInfo(collectorClass) }

        override fun invoke(fuData: FeatureUsageData) {
          fuData.addAutomatedPluginVersion(pluginInfo)
        }
      }
      return Pair(AutomatedPluginVersion, dataSupplier)
    }
  }
}
