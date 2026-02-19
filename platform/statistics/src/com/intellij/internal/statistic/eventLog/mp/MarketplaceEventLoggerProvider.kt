// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.mp

import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit


const val MP_RECORDER_ID = "MP"

class MarketplaceEventLoggerProvider : StatisticsEventLoggerProvider(
  recorderId = MP_RECORDER_ID,
  version = 1,
  sendFrequencyMs = TimeUnit.MINUTES.toMillis(10),
  maxFileSizeInBytes = 10 * 1024,
  sendLogsOnIdeClose = true,
  isCharsEscapingRequired = false
) {
  override fun isRecordEnabled(): Boolean {
    val app = ApplicationManager.getApplication()
    return !app.isUnitTestMode && StatisticsUploadAssistant.isCollectAllowed() &&
           (ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct())
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }
}