// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.registry.Registry

class FeatureUsageEventLoggerProvider : StatisticsEventLoggerProvider("FUS", 20) {
  override fun isRecordEnabled(): Boolean {
    return !ApplicationManager.getApplication().isUnitTestMode &&
           Registry.`is`("feature.usage.event.log.collect.and.upload") &&
           StatisticsUploadAssistant.isCollectAllowed()
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }
}