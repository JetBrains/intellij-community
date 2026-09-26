// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.playSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.awt.Toolkit

/**
 * @author Konstantin Bulenkov
 */
internal class NotificationsBeeper: Notifications {
  override fun notify(notification: Notification) {
    if (isSoundEnabled() && NotificationsConfigurationImpl.getSettings(notification.groupId).isPlaySound) {
      service<NotificationSoundPlayer>().play(notification)
    }
  }
}

@Service
private class NotificationSoundPlayer(private val scope: CoroutineScope) {
  fun play(notification: Notification) {
    val ep = NotificationSoundEP.EP_NAME.extensionList.firstOrNull { it.group == notification.groupId }
    val path = ep?.sound ?: when (notification.type) {
      NotificationType.INFORMATION, NotificationType.IDE_UPDATE -> "sounds/notification_info.wav"
      NotificationType.WARNING -> "sounds/notification_warning.wav"
      NotificationType.ERROR -> "sounds/notification_error.wav"
    }
    val url = (if (ep == null) javaClass.classLoader else ep.pluginDescriptor?.pluginClassLoader)?.getResource(path)
    scope.launch {
      runCatching {
        playSound { checkNotNull(url) { "Sound resource not found: $path" }.openStream() }
      }.getOrHandleException { e ->
        LOG.warn("Cannot play the notification sound '$path', the system beep is used instead.", e)
        Toolkit.getDefaultToolkit().beep()
      }
    }
  }
}

private val LOG = logger<NotificationsBeeper>()
