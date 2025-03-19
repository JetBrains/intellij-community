package com.intellij.driver.sdk

import com.intellij.driver.client.Remote

@Remote("com.jetbrains.performancePlugin.utils.NotificationUtils")
interface NotificationUtils {

  fun disableAllBalloonNotifications()

  fun disableBalloonNotificationsByGroupIdPattern(groupIdsPattern: String)

  fun disableAllBalloonNotificationsWithExcludesPattern(excludesPattern: String)
}