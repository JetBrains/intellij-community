// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl.ui

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.soundSignals.IdeSoundSignals
import com.intellij.ide.soundSignals.PendingSoundSignals
import com.intellij.ide.soundSignals.SOUND_SIGNALS_ENABLED_REGISTRY_KEY
import com.intellij.ide.soundSignals.SoundSignalsSettingsState
import com.intellij.ide.soundSignals.loadSoundSignals
import com.intellij.ide.soundSignals.setSupportScreenReaders
import com.intellij.ide.soundSignals.soundSignals
import com.intellij.ide.soundSignals.withSoundSignalsSettings
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.impl.NotificationSettings
import com.intellij.notification.impl.NotificationSoundEP
import com.intellij.notification.impl.NotificationsConfigurationImpl
import com.intellij.openapi.Disposable
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.ui.layout.ComponentPredicate
import com.intellij.util.ui.UIUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.JCheckBox

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class NotificationSettingsUiTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @BeforeEach
  fun bindGroups() {
    val bindings = listOf(BOUND_GROUP, SIBLING_GROUP).map { groupId ->
      NotificationSoundEP().apply {
        group = groupId
        soundSignal = SIGNAL.id
      }
    }
    ExtensionTestUtil.addExtensions(NotificationSoundEP.EP_NAME, bindings, disposable)
  }

  @Test
  fun `the Play sound checkbox of a bound group edits the signal and not the group`(): Unit = uiTest { settings ->
    val page = Page(wrapper(BOUND_GROUP))
    assertThat(page.playSound.isSelected).isFalse()

    page.playSound.doClick()
    assertThat(page.playSound.isSelected).isTrue()
    assertThat(page.wrapper.hasChanged()).isFalse()
    assertThat(page.soundSignals.isModified()).isTrue()

    page.soundSignals.apply()
    assertThat(settings.soundSignals).isEqualTo(SoundSignalsSettingsState(signals = mapOf(SIGNAL.id to true)))
    assertThat(NotificationsConfigurationImpl.getSettings(BOUND_GROUP).isPlaySound).isFalse()
  }

  @Test
  fun `groups bound to one signal show the same choice`(): Unit = uiTest {
    val page = Page(wrapper(BOUND_GROUP))
    page.playSound.doClick()

    page.ui.updateUi(wrapper(SIBLING_GROUP))

    assertThat(page.playSound.isSelected).isTrue()
  }

  @Test
  fun `a legacy Play sound of a bound group shows checked`(): Unit = uiTest {
    NotificationsConfigurationImpl.getInstanceImpl()
      .changeSettings(NotificationSettings(SIBLING_GROUP, NotificationDisplayType.NONE, false, false, true))
    try {
      assertThat(Page(wrapper(BOUND_GROUP)).playSound.isSelected).isTrue()
    }
    finally {
      NotificationsConfigurationImpl.remove(SIBLING_GROUP)
    }
  }

  @Test
  fun `the checkbox of an unbound group edits the group`(): Unit = uiTest {
    val page = Page(wrapper(UNBOUND_GROUP))

    page.playSound.doClick()

    assertThat(page.wrapper.isPlaySound).isTrue()
    assertThat(page.soundSignals.isModified()).isFalse()
  }

  @Test
  fun `without sound signals a bound group edits its own Play sound`(): Unit = uiTest {
    val wrapper = wrapper(BOUND_GROUP)
    val ui = NotificationSettingsUi(wrapper, ComponentPredicate.TRUE)
    val playSound = playSoundCheckBox(ui)

    playSound.doClick()

    assertThat(wrapper.isPlaySound).isTrue()
  }

  private fun uiTest(body: (AccessibilitySettings) -> Unit): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(false)
    settings.loadSoundSignals(SoundSignalsSettingsState())
    body(settings)
  }

  private fun wrapper(groupId: String) = NotificationSettingsWrapper(NotificationsConfigurationImpl.getSettings(groupId))

  private inner class Page(val wrapper: NotificationSettingsWrapper) {
    val soundSignals: PendingSoundSignals = PendingSoundSignals().apply { view { ui.renderPlaySound() } }
    val ui: NotificationSettingsUi = NotificationSettingsUi(wrapper, ComponentPredicate.TRUE, soundSignals)
    val playSound: JCheckBox = playSoundCheckBox(ui)
  }

  private fun playSoundCheckBox(ui: NotificationSettingsUi): JCheckBox =
    UIUtil.findComponentsOfType(ui.ui, JCheckBox::class.java).single { it.text == IdeBundle.message("notifications.configurable.play.sound") }

  private companion object {
    const val BOUND_GROUP = "NotificationSettingsUiTest bound group"
    const val SIBLING_GROUP = "NotificationSettingsUiTest sibling group"
    const val UNBOUND_GROUP = "NotificationSettingsUiTest unbound group"
    val SIGNAL = IdeSoundSignals.BUILD_FINISHED
  }
}
