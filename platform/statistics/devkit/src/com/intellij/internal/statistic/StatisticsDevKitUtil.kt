// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.StatisticsEventLogProviderUtil
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider
import com.intellij.internal.statistic.utils.StatisticsRecorderUtil
import com.intellij.notification.NotificationBuilder
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
    val title = StatisticsBundle.message("stats.feature.usage.statistics")
    NotificationBuilder(STATISTICS_NOTIFICATION_GROUP_ID, title, message, type).buildAndNotify(project)
  }
}