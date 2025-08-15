// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.trace

import com.intellij.ide.ConsentOptionsProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit

internal class TraceEventLoggerProvider : StatisticsEventLoggerProvider(
  recorderId = RECORDER_ID,
  version = 2,
  sendFrequencyMs = TimeUnit.MINUTES.toMillis(10),
  maxFileSizeInBytes = 10 * 1024,
  sendLogsOnIdeClose = true,
  isCharsEscapingRequired = false,
  useDefaultRecorderId = true,
) {
  companion object {
    const val RECORDER_ID: String = "TRACE"
  }

  override fun isRecordEnabled(): Boolean =
    !ApplicationManager.getApplication().isUnitTestMode &&
    (ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct()) &&
    StatisticsUploadAssistant.isCollectAllowed { service<ConsentOptionsProvider>().isTraceDataCollectionAllowed }

  override fun isSendEnabled(): Boolean =
    isRecordEnabled() &&
    StatisticsUploadAssistant.isSendAllowed { service<ConsentOptionsProvider>().isTraceDataCollectionAllowed }
}