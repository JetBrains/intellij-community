package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote

fun Driver.disableAllBalloonNotifications() {
  utility(NotificationUtils::class).disableAllBalloonNotifications()
}

fun Driver.closeAndDisableAllBalloonNotifications() {
  disableAllBalloonNotifications()
  runCatching { invokeAction("CloseAllNotifications") }
}

@Remote("com.jetbrains.performancePlugin.utils.NotificationUtils")
interface NotificationUtils {

  fun disableAllBalloonNotifications()

  fun disableBalloonNotificationsByGroupIdPattern(groupIdsPattern: String)

  fun disableAllBalloonNotificationsWithExcludesPattern(excludesPattern: String)
}