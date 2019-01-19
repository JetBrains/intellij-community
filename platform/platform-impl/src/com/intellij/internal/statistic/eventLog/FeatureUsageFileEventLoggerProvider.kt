// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry

class FeatureUsageFileEventLoggerProvider : FeatureUsageEventLoggerProvider {
  override fun createLogger(): FeatureUsageEventLogger {
    val config = EventLogConfiguration
    val bucket = config.bucket.toString()
    val logger = FeatureUsageFileEventLogger(config.sessionId, config.build, bucket, "2", FeatureUsageLogEventWriter())
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }

  override fun isEnabled(): Boolean {
    return !ApplicationManager.getApplication().isUnitTestMode &&
           Registry.`is`("feature.usage.event.log.collect.and.upload") &&
           StatisticsUploadAssistant.isSendAllowed()
  }
}