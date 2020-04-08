// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project

object StatisticsDevKitUtil {
  const val DEFAULT_RECORDER = "FUS"

  fun showNotification(project: Project, type: NotificationType, message: String) {
    val title = StatisticsBundle.message("stats.feature.usage.statistics")
    Notifications.Bus.notify(Notification("FeatureUsageStatistics", title, message, type), project)
  }
}