// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.FeatureUsageData
import com.jetbrains.changeReminder.predict.PredictionData

internal class ChangeReminderNodeExpandedEvent(
  private val displayedPredictionResult: PredictionData
) : ChangeReminderUserEvent {
  override val eventType = ChangeReminderEventType.NODE_EXPANDED

  override fun addToLogData(logData: FeatureUsageData) {
    logData.addPredictionData(displayedPredictionResult)
  }
}