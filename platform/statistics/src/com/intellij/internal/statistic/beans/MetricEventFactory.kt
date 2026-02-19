// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.beans

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

/**
 * Creates a multi-dimensional metric or a metric with a single but common event data, e.g.
 *
 * eventId="breakpoint", eventData={"type":"line", "lang":"Java", "count":5}
 * eventId="gradle", eventData={"version":"2.3.1"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, data: FeatureUsageData?): MetricEvent {
  return MetricEvent(eventId, data, "FUS")
}

/**
 * Creates a enum-like string metrics, e.g.
 *
 * eventId="upload.files", eventData={"value":"ON_SAVE"}
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use EventLogGroup#registerEvent and EventId#metric instead")
fun newMetric(@NonNls eventId: String, value: String): MetricEvent {
  return MetricEvent(eventId, FeatureUsageData("FUS").addValue(value), "FUS")
}

