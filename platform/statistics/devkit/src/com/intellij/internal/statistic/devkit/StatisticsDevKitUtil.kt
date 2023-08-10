// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.devkit

import com.intellij.internal.statistic.StatisticsBundle
import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object StatisticsDevKitUtil {
  const val DEFAULT_RECORDER = "FUS"
  const val STATISTICS_NOTIFICATION_GROUP_ID = "FeatureUsageStatistics"

  fun getLogProvidersInTestMode(): List<StatisticsEventLoggerProvider> {
    val allowedRecorders = StatisticsRecorderUtil.getRecordersInTestMode()
    if (allowedRecorders.isEmpty()) {
      return emptyList()
    }
    return StatisticsEventLogProviderUtil.getEventLogProviders().filter { allowedRecorders.contains(it.recorderId) }
  }

  fun showNotification(project: Project, type: NotificationType, message: String) {
    Notification(STATISTICS_NOTIFICATION_GROUP_ID, StatisticsBundle.message("stats.feature.usage.statistics"), message, type)
      .notify(project)
  }
}
