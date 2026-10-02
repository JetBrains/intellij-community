// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.soundSignals.IdeSoundSignals
import com.intellij.ide.soundSignals.SOUND_SIGNALS_ENABLED_REGISTRY_KEY
import com.intellij.ide.soundSignals.SoundSignalsSettingsState
import com.intellij.ide.soundSignals.loadSoundSignals
import com.intellij.ide.soundSignals.setSupportScreenReaders
import com.intellij.ide.soundSignals.withSoundSignalsSettings
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class NotificationsBeeperTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private val pluginLoader = object : ClassLoader(null) {
    val requested = CopyOnWriteArrayList<String>()

    override fun getResource(name: String): URL? {
      requested.add(name)
      return null
    }
  }

  @BeforeEach
  fun bindSound() {
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

    assertThat(pluginLoader.requested).containsExactly("sounds/bound.wav")
  }

  @Test
  fun `a group with Play sound off plays nothing`() {
    NotificationsBeeper().notify(Notification(BOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(pluginLoader.requested).isEmpty()
  }

  @Test
  fun `a binding does not apply to another group`() {
    enablePlaySound(UNBOUND_GROUP)

    NotificationsBeeper().notify(Notification(UNBOUND_GROUP, "title", NotificationType.ERROR))

    assertThat(pluginLoader.requested).isEmpty()
  }

  @Test
  fun `a group bound to a signal follows the signal and not its own Play sound`(): Unit = signalTest { settings ->
    enablePlaySound(SIGNAL_GROUP)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to false)))
    notifySignalGroup()
    assertThat(pluginLoader.requested).isEmpty()

    NotificationsConfigurationImpl.remove(SIGNAL_GROUP)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))
    notifySignalGroup()
    assertThat(pluginLoader.requested).containsExactly("sounds/signal.wav")
  }

  @Test
  fun `a legacy Play sound of a group bound to a signal plays through the signal`(): Unit = signalTest {
    enablePlaySound(SIGNAL_GROUP)

    notifySignalGroup()

    assertThat(pluginLoader.requested).containsExactly("sounds/signal.wav")
  }

  @Test
  fun `a played signal of a group is reported`(): Unit = signalTest { settings ->
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))

    val events = FUCollectorTestCase.collectLogEvents(testDisposable) { notifySignalGroup() }
      .filter { it.group.id == "accessibility" && it.event.id == "sound.signal.played" }

    assertThat(events.map { it.event.data["signal"] }).containsExactly(SIGNAL.id)
  }

  @Test
  fun `with the feature off a group bound to a signal keeps its own Play sound`(): Unit = signalTest { settings ->
    Registry.get(SOUND_SIGNALS_ENABLED_REGISTRY_KEY).setValue(false, testDisposable)
    settings.loadSoundSignals(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))
    notifySignalGroup()
    assertThat(pluginLoader.requested).isEmpty()

    enablePlaySound(SIGNAL_GROUP)
    notifySignalGroup()
    assertThat(pluginLoader.requested).containsExactly("sounds/signal.wav")
  }

  private fun signalTest(body: (AccessibilitySettings) -> Unit): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(false)
    settings.loadSoundSignals(SoundSignalsSettingsState())
    body(settings)
  }

  private fun notifySignalGroup() {
    NotificationsBeeper().notify(Notification(SIGNAL_GROUP, "title", NotificationType.INFORMATION))
  }

  private fun enablePlaySound(groupId: String) {
    NotificationsConfigurationImpl.getInstanceImpl()
      .changeSettings(NotificationSettings(groupId, NotificationDisplayType.BALLOON, false, false, true))
    Disposer.register(testDisposable) { NotificationsConfigurationImpl.remove(groupId) }
  }

  private companion object {
    const val BOUND_GROUP = "NotificationsBeeperTest bound group"
    const val UNBOUND_GROUP = "NotificationsBeeperTest unbound group"
    const val SIGNAL_GROUP = "NotificationsBeeperTest signal group"
    val SIGNAL = IdeSoundSignals.BUILD_FINISHED
  }
}
