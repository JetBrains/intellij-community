// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationAdapter
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.registry.Registry
import java.util.*

class FeatureUsageFileEventLoggerProvider : FeatureUsageEventLoggerProvider {
  override fun createLogger(): FeatureUsageEventLogger {
    val sessionId = UUID.randomUUID().toString().shortedUUID()
    val build = ApplicationInfo.getInstance().build.asBuildNumber()
    val logger = FeatureUsageFileEventLogger(sessionId, build, "-1", "2", FeatureUsageLogEventWriter())

    ApplicationManager.getApplication().addApplicationListener(object : ApplicationAdapter() {
      override fun applicationExiting() {
        logger.dispose()
      }
    })
    return logger
  }

  private fun String.shortedUUID(): String {
    val start = this.lastIndexOf('-')
    if (start > 0 && start + 1 < this.length) {
      return this.substring(start + 1)
    }
    return this
  }

  private fun BuildNumber.asBuildNumber(): String {
    val str = this.asStringWithoutProductCodeAndSnapshot()
    return if (str.endsWith(".")) str + "0" else str
  }

  override fun isEnabled(): Boolean {
    return StatisticsUploadAssistant.isSendAllowed() &&
           Registry.`is`("feature.usage.event.log.collect.and.upload") &&
           !ApplicationManager.getApplication().isUnitTestMode
  }
}