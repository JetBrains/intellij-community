// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog.fus

import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.statistic.JetBrainsConsentProvider
import com.intellij.internal.statistic.eventLog.FilteredEventMergeStrategy
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProviderExt
import com.intellij.internal.statistic.eventLog.StatisticsEventMergeStrategy
import com.intellij.internal.statistic.eventLog.events.EventFieldIds
import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceOrNull
import com.intellij.util.PlatformUtils
import java.util.concurrent.TimeUnit

internal class FeatureUsageEventLoggerProvider : StatisticsEventLoggerProviderExt(
  recorderId = "FUS",
  version = 76,
  sendFrequencyMs = TimeUnit.MINUTES.toMillis(15),
  maxFileSizeInBytes = DEFAULT_MAX_FILE_SIZE_BYTES,
  sendLogsOnIdeClose = true) {

  override fun isRecordEnabled(): Boolean = when {
    ApplicationManager.getApplication().isUnitTestMode -> false
    ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct() -> StatisticsUploadAssistant.isCollectAllowed()
    isCompatibleVendor() -> StatisticsUploadAssistant.isCollectAllowed { isAllowedByUserConsentWithJetBrains() }
    else -> false
  }

  override fun isSendEnabled(): Boolean = when {
    !isRecordEnabled() -> false
    ApplicationInfo.getInstance() == null || PlatformUtils.isJetBrainsProduct() -> StatisticsUploadAssistant.isSendAllowed()
    isCompatibleVendor() -> StatisticsUploadAssistant.isSendAllowed { isAllowedByUserConsentWithJetBrains() }
    else -> false
  }

  private fun isAllowedByUserConsentWithJetBrains(): Boolean {
    val consentProvider = serviceOrNull<JetBrainsConsentProvider>() ?: return false
    val providerPlugin = PluginManager.getPluginByClass(consentProvider.javaClass) ?: return false
    if (!PluginManagerCore.isDevelopedExclusivelyByJetBrains(providerPlugin)) return false
    return consentProvider.isAllowedByUserConsentWithJetBrains()
  }

  private fun isCompatibleVendor(): Boolean = "AndroidStudio" == PlatformUtils.getPlatformPrefix()

  override fun createEventsMergeStrategy(): StatisticsEventMergeStrategy {
    // this happens rather early on startup, do not touch EventFields.*
    val ignoredFields = EventFieldIds.FieldsIgnoredByMerge.toSet()
    return FilteredEventMergeStrategy(ignoredFields)
  }
}