// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.*
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry

class FeatureUsageFileEventLoggerProvider : StatisticsEventLoggerProvider {
  override fun createLogger(): StatisticsEventLogger {
    val config = EventLogConfiguration
    val bucket = config.bucket.toString()
    val version = config.version.toString()
    val logger = StatisticsFileEventLogger(config.sessionId, config.build, bucket, version, StatisticsEventLogFileWriter())
    Disposer.register(ApplicationManager.getApplication(), logger)
    return logger
  }

  override fun isEnabled(): Boolean {
    return !ApplicationManager.getApplication().isUnitTestMode &&
           Registry.`is`("feature.usage.event.log.collect.and.upload") &&
           StatisticsUploadAssistant.isCollectAllowed()
  }
}