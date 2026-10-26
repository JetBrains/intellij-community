package com.intellij.driver.sdk

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.common.waitForIdeFrameReady

fun Driver.disableAllBalloonNotifications() {
  utility(NotificationUtils::class).disableAllBalloonNotifications()
}

fun Driver.closeAndDisableAllBalloonNotifications() {
  waitForIdeFrameReady()
  disableAllBalloonNotifications()
  closeAllNotifications()
}

fun Driver.closeAllNotifications() {
  runCatching { invokeAction("CloseAllNotifications") }
}

@Remote("com.jetbrains.performancePlugin.utils.NotificationUtils")
interface NotificationUtils {

  fun disableAllBalloonNotifications()

  fun enableAllBalloonNotifications()

  fun disableBalloonNotificationsByGroupIdPattern(groupIdsPattern: String)

  fun disableAllBalloonNotificationsWithExcludesPattern(excludesPattern: String)
}