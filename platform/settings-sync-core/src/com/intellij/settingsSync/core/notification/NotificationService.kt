package com.intellij.settingsSync.core.notification

import com.intellij.openapi.components.service
import com.intellij.settingsSync.core.RestartReason

internal interface NotificationService {
  companion object {
    fun getInstance(): NotificationService = service<NotificationService>()
  }

  fun notifySateRestoreFailed()
  fun notifyZipSizeExceed()
  fun notifyRestartNeeded(reasons: Collection<RestartReason>)
}