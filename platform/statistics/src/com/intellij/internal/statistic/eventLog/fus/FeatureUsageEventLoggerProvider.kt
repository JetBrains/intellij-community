// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.TimeUnit

internal class FeatureUsageEventLoggerProvider : StatisticsEventLoggerProvider("FUS", 48, sendFrequencyMs = TimeUnit.MINUTES.toMillis(15)) {
  override fun isRecordEnabled(): Boolean {
    return !ApplicationManager.getApplication().isHeadlessEnvironment &&
           StatisticsUploadAssistant.isCollectAllowed()
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }
}