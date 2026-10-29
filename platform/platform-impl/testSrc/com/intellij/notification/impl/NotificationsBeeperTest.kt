// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.soundSignals.IdeSoundSignals
import com.intellij.ide.soundSignals.SOUND_SIGNALS_ENABLED_REGISTRY_KEY
import com.intellij.ide.soundSignals.SoundSignalsSettingsState
import com.intellij.ide.soundSignals.loadSoundSignals
import com.intellij.ide.soundSignals.notificationSound
import com.intellij.ide.soundSignals.setSupportScreenReaders
import com.intellij.ide.soundSignals.withSoundSignalsSettings
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import kotlinx.coroutines.Job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class NotificationsBeeperTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private val pluginLoader = object : ClassLoader(null) {}

  private val player = RecordingPlayer()

  @BeforeEach
  fun bindSound() {
    ApplicationManager.getApplication().replaceService(NotificationSoundPlayer::class.java, player, testDisposable)
    val descriptor = DefaultPluginDescriptor(PluginId.getId("test.plugin"), pluginLoader)
    val soundBinding = NotificationSoundEP().apply {
      group = BOUND_GROUP
      sound = "sounds/bound.wav"
      setPluginDescriptor(descriptor)
    }
    val signalBinding = NotificationSoundEP().apply {
      group = SIGNAL_GROUP
      sound = "sounds/signal.wav"
      soundSignal = SIGNAL.id
      setPluginDescriptor(descriptor)
    }
    ExtensionTestUtil.addExtensions(NotificationSoundEP.EP_NAME, listOf(soundBinding, signalBinding), testDisposable)
  }

  @Test
  fun `a bound group plays its sound from the plugin that binds it`() {
    enablePlaySound(BOUND_GROUP)

    NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(player.played).containsExactly(pluginLoader to "sounds/bound.wav")
  }

  @Test
  fun `a group with Play sound off plays nothing`() {
    NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(player.played).isEmpty()
  }

  @Test
  fun `a binding does not apply to another group`() {
    enablePlaySound(UNBOUND_GROUP)

    NotificationsBeeper().notify(Notification(UNBOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(player.played.map { it.second }).containsExactly(notificationSound(NotificationType.ERROR))
  }

  @Test
  fun `a group bound to a signal follows the signal and not its own Play sound`(): Unit = signalTest { settings ->
    enablePlaySound(SIGNAL_GROUP)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to false)))
    notifySignalGroup()
    assertThat(player.played).isEmpty()

    NotificationsConfigurationImpl.remove(SIGNAL_GROUP)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))
    notifySignalGroup()
    assertThat(player.played).containsExactly(pluginLoader to "sounds/signal.wav")
  }

  @Test
  fun `a legacy Play sound of a group bound to a signal plays through the signal`(): Unit = signalTest {
    enablePlaySound(SIGNAL_GROUP)

    notifySignalGroup()

    assertThat(player.played).containsExactly(pluginLoader to "sounds/signal.wav")
  }

  @Test
  fun `a played signal of a group is reported`(): Unit = signalTest { settings ->
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))

    val events = FUCollectorTestCase.collectLogEvents(testDisposable) { notifySignalGroup() }
      .filter { it.group.id == "accessibility" && it.event.id == "sound.signal.played" }

    assertThat(events.map { it.event.data["signal"] }).containsExactly(SIGNAL.id)
  }

  @Test
  fun `a played sound of a group is reported`() {
    enablePlaySound(BOUND_GROUP, NotificationDisplayType.NONE)

    val events = soundPlayedEvents { NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR)) }

    assertThat(events.map { listOf(it.event.data["notification_group"], it.event.data["display_type"], it.event.data["severity"]) })
      .containsExactly(listOf(BOUND_GROUP, "NONE", "ERROR"))
  }

  @Test
  fun `with the feature off a group bound to a signal keeps its own Play sound`(): Unit = signalTest { settings ->
    Registry.get(SOUND_SIGNALS_ENABLED_REGISTRY_KEY).setValue(false, testDisposable)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))
    notifySignalGroup()
    assertThat(player.played).isEmpty()

    enablePlaySound(SIGNAL_GROUP)
    notifySignalGroup()
    assertThat(player.played).containsExactly(pluginLoader to "sounds/signal.wav")
  }

  @Test
  fun `the preview of a group with its own sound plays that sound`() {
    player.preview(BOUND_GROUP)

    assertThat(player.played).containsExactly(pluginLoader to "sounds/bound.wav")
  }

  @Test
  fun `the preview of any other group plays every notification type in turn, also with Play sound off`() {
    player.preview(UNBOUND_GROUP)

    val loader = NotificationSoundPlayer::class.java.classLoader
    val types = listOf(NotificationType.INFORMATION, NotificationType.WARNING, NotificationType.ERROR)
    assertThat(player.played).containsExactlyElementsOf(types.map { loader to notificationSound(it) })
  }

  private fun signalTest(body: (AccessibilitySettings) -> Unit): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(false)
    settings.loadSoundSignals(SoundSignalsSettingsState())
    body(settings)
  }

  private fun soundPlayedEvents(action: () -> Unit): List<LogEvent> =
    FUCollectorTestCase.collectLogEvents(testDisposable, action)
      .filter { it.group.id == "notifications" && it.event.id == "sound.played" }

  private fun notifySignalGroup() {
    NotificationsBeeper().notify(Notification(SIGNAL_GROUP, "title", NotificationType.INFORMATION))
  }

  private fun enablePlaySound(groupId: String, displayType: NotificationDisplayType = NotificationDisplayType.BALLOON) {
    NotificationsConfigurationImpl.getInstanceImpl()
      .changeSettings(NotificationSettings(groupId, displayType, false, false, true))
    Disposer.register(testDisposable) { NotificationsConfigurationImpl.remove(groupId) }
  }

  private class RecordingPlayer : NotificationSoundPlayer() {
    val played = ArrayList<Pair<ClassLoader?, String>>()

    override fun playSoundResources(paths: List<String>, loader: ClassLoader?): Job {
      played += paths.map { loader to it }
      return Job()
    }
  }

  private companion object {
    const val BOUND_GROUP = "NotificationsBeeperTest bound group"
    const val UNBOUND_GROUP = "NotificationsBeeperTest unbound group"
    const val SIGNAL_GROUP = "NotificationsBeeperTest signal group"
    val SIGNAL = IdeSoundSignals.BUILD_FINISHED
  }
}
