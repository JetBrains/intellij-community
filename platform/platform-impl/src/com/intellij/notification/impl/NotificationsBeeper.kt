// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.accessibility.AccessibilityUsageTrackerCollector
import com.intellij.ide.soundSignals.SoundSignal
import com.intellij.ide.soundSignals.findSoundSignal
import com.intellij.ide.soundSignals.isSoundSignalOn
import com.intellij.ide.soundSignals.isSoundSignalsFeatureEnabled
import com.intellij.ide.soundSignals.notificationSound
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.Notifications
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
    val settings = NotificationsConfigurationImpl.getSettings(notification.groupId)
    if (playsSound(settings)) {
      service<NotificationSoundPlayer>().play(notification, settings.displayType)
      boundSoundSignal(notification.groupId)?.let { AccessibilityUsageTrackerCollector.SOUND_SIGNAL_PLAYED.log(it.id) }
    }
  }
}

internal fun playsSound(settings: NotificationSettings): Boolean =
  isSoundEnabled() && (boundSoundSignal(settings.groupId)?.let(::isSoundSignalOn) ?: settings.isPlaySound)

private fun boundSoundSignal(groupId: String): SoundSignal? =
  if (isSoundSignalsFeatureEnabled()) soundSignalIdOf(groupId)?.let(::findSoundSignal) else null

internal abstract class NotificationSoundPlayer {
  fun play(notification: Notification, displayType: NotificationDisplayType) {
    val ep = findNotificationSound(notification.groupId)
    val sound = ep?.sound
    val path = sound ?: notificationSound(notification.type)
    playSoundResource(path, if (sound == null) NotificationSoundPlayer::class.java.classLoader else ep.pluginDescriptor?.pluginClassLoader)
    NotificationCollector.getInstance().logSoundPlayed(notification, displayType)
  }

  protected abstract fun playSoundResource(path: String, loader: ClassLoader?)
}

internal class NotificationSoundPlayerImpl(private val scope: CoroutineScope) : NotificationSoundPlayer() {
  override fun playSoundResource(path: String, loader: ClassLoader?) {
    val url = loader?.getResource(path)
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
