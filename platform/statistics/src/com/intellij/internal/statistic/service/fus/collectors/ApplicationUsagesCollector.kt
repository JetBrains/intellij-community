// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

/**
 * Use it to create a collector which records IDE state.
 * To implement a new collector:
 *
 *  1. Inherit the class, implement [ApplicationUsagesCollector.getGroup] and [ApplicationUsagesCollector.getMetrics] and register collector in plugin.xml.
 * See *fus-collectors.md* for more details.
 *  1. Implement custom validation rules if necessary. For more information see [IntellijSensitiveDataValidator].
 *  1. If new group is implemented in a platform or a plugin built with IntelliJ Ultimate, YT issue will be created automatically
 *  1. Otherwise, create a YT issue in FUS project with group data scheme and descriptions to register it on the statistics metadata server
 *
 * To test collector:
 *
 *  1. Open "Statistics Event Log" toolwindow.
 *
 *  2. Add group to events test scheme with "Add Group to Events Test Scheme" action.<br></br>
 * [com.intellij.internal.statistic.devkit.actions.scheme.AddGroupToTestSchemeAction]
 *
 *  3. Record all state collectors with "Record State Collectors to Event Log" action.<br></br>
 * [com.intellij.internal.statistic.devkit.actions.RecordStateStatisticsEventLogAction]
 *
 * For more information see *fus-collectors.md*
 *
 * @see ProjectUsagesCollector
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
abstract class ApplicationUsagesCollector : FeatureUsagesCollector() {
  /**
   * Implement this method to calculate metrics.
   * <br></br><br></br>
   * [MetricEvent.eventId] should indicate what we measure, e.g. "configured.vcs", "module.jdk".<br></br>
   * [MetricEvent.data] should contain the value of the measurement, e.g. {"name":"Git"}, {"version":"1.8", "vendor":"OpenJdk"}
   *
   * @return **Not empty** set of metrics
   */
  open fun getMetrics(): Set<MetricEvent> {
    throw AbstractMethodError()
  }

  open suspend fun getMetricsAsync(): Set<MetricEvent> {
    return getMetrics()
  }

  companion object {
    @ApiStatus.Internal
    val EP_NAME = ExtensionPointName<ApplicationUsagesCollector>("com.intellij.statistics.applicationUsagesCollector")

    internal fun getExtensions(invoker: UsagesCollectorConsumer): Set<ApplicationUsagesCollector> {
      return getExtensions(invoker, EP_NAME)
    }

    internal fun getExtensions(invoker: UsagesCollectorConsumer, allowedOnStartupOnly: Boolean): Set<ApplicationUsagesCollector> {
      return getExtensions(invoker, EP_NAME, allowedOnStartupOnly)
    }
  }
}