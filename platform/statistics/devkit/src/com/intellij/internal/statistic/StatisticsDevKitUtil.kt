// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

internal object StatisticsDevKitUtil {
  const val DEFAULT_RECORDER = "FUS"
  val STATISTICS_NOTIFICATION_GROUP = NotificationGroup.balloonGroup("FeatureUsageStatistics",
                                                                     StatisticsBundle.message("stats.feature.usage.statistics"))

  fun showNotification(project: Project, type: NotificationType, message: String) {
    val title = StatisticsBundle.message("stats.feature.usage.statistics")
    STATISTICS_NOTIFICATION_GROUP.createNotification(title, message, type).notify(project)
  }
}