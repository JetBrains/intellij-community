// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.accessibility.AccessibilityUsageTrackerCollector
import com.intellij.ide.soundSignals.IdeSounds
import com.intellij.ide.soundSignals.PREVIEW_DELAY
import com.intellij.ide.soundSignals.SoundSignal
import com.intellij.ide.soundSignals.findSoundSignal
import com.intellij.ide.soundSignals.isSoundSignalOn
import com.intellij.ide.soundSignals.isSoundSignalsFeatureEnabled
import com.intellij.ide.soundSignals.notificationSound
import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ui.playSound
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.util.concurrent.atomic.AtomicReference

/**
 * @author Konstantin Bulenkov
 */
internal class NotificationsBeeper: Notifications {
  override fun notify(notification: Notification) {
    val settings = NotificationsConfigurationImpl.getSettings(notification.groupId)
    if (playsSound(settings)) {
      service<NotificationSoundPlayer>().play(notification)
      NotificationCollector.getInstance().logSoundPlayed(notification, settings.displayType)
      boundSoundSignal(notification.groupId)?.let { AccessibilityUsageTrackerCollector.SOUND_SIGNAL_PLAYED.log(it.id) }
    }
  }
}

internal fun playsSound(settings: NotificationSettings): Boolean =
  isSoundEnabled() && (boundSoundSignal(settings.groupId)?.let(::isSoundSignalOn) ?: settings.isPlaySound)

private fun boundSoundSignal(groupId: String): SoundSignal? =
  if (isSoundSignalsFeatureEnabled()) soundSignalIdOf(groupId)?.let(::findSoundSignal) else null

@Service
internal class NotificationSoundPlayer(private val scope: CoroutineScope) {
  private val previewJob = AtomicReference<Job?>()

  fun play(notification: Notification) {
    playOwnSound(notification.groupId) ?: playSoundResources(listOf(notificationSound(notification.type)), javaClass.classLoader)
  }

  fun preview(groupId: String) {
    previewJob.getAndSet(playOwnSound(groupId) ?: playSoundResources(IdeSounds.NOTIFICATION_SOUNDS, javaClass.classLoader))?.cancel()
  }

  private fun playOwnSound(groupId: String): Job? {
    val ep = findNotificationSound(groupId)
    val sound = ep?.sound ?: return null
    return playSoundResources(listOf(sound), ep.pluginDescriptor?.pluginClassLoader)
  }

  private fun playSoundResources(paths: List<String>, loader: ClassLoader?): Job {
    val urls = paths.map { it to loader?.getResource(it) }
    return scope.launch {
      for ((index, entry) in urls.withIndex()) {
        val (path, url) = entry
        if (index > 0) delay(PREVIEW_DELAY)
        runCatching {
          playSound { checkNotNull(url) { "Sound resource not found: $path" }.openStream() }
        }.getOrHandleException { e ->
          LOG.warn("Cannot play the notification sound '$path', the system beep is used instead.", e)
          Toolkit.getDefaultToolkit().beep()
        }
      }
    }
  }
}

private val LOG = logger<NotificationsBeeper>()
