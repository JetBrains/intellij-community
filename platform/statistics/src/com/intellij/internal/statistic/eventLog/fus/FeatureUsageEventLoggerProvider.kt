// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.internal.statistic.eventLog.FilteredEventMergeStrategy
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.eventLog.StatisticsEventMergeStrategy
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit

internal class FeatureUsageEventLoggerProvider : StatisticsEventLoggerProvider("FUS", 73, sendFrequencyMs = TimeUnit.MINUTES.toMillis(15), DEFAULT_MAX_FILE_SIZE_BYTES) {
  override fun isRecordEnabled(): Boolean {
    return !ApplicationManager.getApplication().isUnitTestMode &&
           StatisticsUploadAssistant.isCollectAllowed() &&
           ((ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct()) || StatisticsUploadAssistant.getCollectAllowedOverride())
  }

  override fun isSendEnabled(): Boolean {
    return isRecordEnabled() && StatisticsUploadAssistant.isSendAllowed()
  }

  override fun createEventsMergeStrategy(): StatisticsEventMergeStrategy {
    val ignoredFields = EventFields.FieldsIgnoredByMerge.map { it.name }.toSet()
    return FilteredEventMergeStrategy(ignoredFields)
  }
}