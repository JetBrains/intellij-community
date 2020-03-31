// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.changeReminder.stats

import com.intellij.internal.statistic.eventLog.FeatureUsageData

internal interface ChangeReminderUserEvent {
  val eventType: ChangeReminderEventType

  fun addToLogData(logData: FeatureUsageData)
}