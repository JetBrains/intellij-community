// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.FilteredEventMergeStrategy
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProviderExt
import com.intellij.internal.statistic.eventLog.StatisticsEventMergeStrategy
import com.intellij.internal.statistic.eventLog.events.EventFieldIds
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit

internal class FeatureUsageEventLoggerProvider : StatisticsEventLoggerProviderExt(
  recorderId = "FUS",
  version = 76,
  sendFrequencyMs = TimeUnit.MINUTES.toMillis(15),
  maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
  sendLogsOnIdeClose = true) {

  override fun isRecordEnabled(): Boolean {
    return !ApplicationManager.getApplication().isUnitTestMode &&
           StatisticsUploadAssistant.isCollectAllowed() &&
           ((ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct()))
  }

  override fun isSendEnabled(): Boolean = isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()

  override fun createEventsMergeStrategy(): StatisticsEventMergeStrategy {
    // this happens rather early on startup, do not touch EventFields.*
    val ignoredFields = EventFieldIds.FieldsIgnoredByMerge.toSet()
    return FilteredEventMergeStrategy(ignoredFields)
  }
}