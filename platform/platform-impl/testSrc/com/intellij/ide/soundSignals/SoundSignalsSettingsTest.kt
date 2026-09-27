// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettingsState
import com.intellij.configurationStore.deserialize
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalsSettingsTest {
  @Test
  fun `the calculated defaults follow screen reader support`(): Unit = withSoundSignalsSettings {
    setSupportScreenReaders(false)
    assertThat(isSoundSignalsOn()).isFalse()

    setSupportScreenReaders(true)
    assertThat(isSoundSignalsOn()).isTrue()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isTrue()
  }

  @Test
  fun `an explicit play choice wins over screen reader support`(): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(true)
    settings.setPlaySignals(false)
    assertThat(isSoundSignalsOn()).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()

    setSupportScreenReaders(false)
    settings.setPlaySignals(true)
    assertThat(isSoundSignalsOn()).isTrue()
  }

  @Test
  fun `a muted signal stays muted while play is on`(): Unit = withSoundSignalsSettings { settings ->
    settings.setPlaySignals(true)
    settings.setSignal(IdeSoundSignals.WARNING_LINE, false)

    assertThat(isSoundSignalOn(IdeSoundSignals.WARNING_LINE)).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isTrue()
  }

  @Test
  fun `the sound signals are stored in their own tag of the accessibility state`() {
    val state = AccessibilitySettingsState(SoundSignalsSettingsState(playSignals = true, signals = mapOf("error.line" to false)))

    assertThat(JDOMUtil.write(serialize(state)!!)).isEqualTo("""
      <AccessibilitySettingsState>
        <soundSignals>
          <option name="playSignals" value="true" />
          <signals>
            <signal id="error.line" enabled="false" />
          </signals>
        </soundSignals>
      </AccessibilitySettingsState>
    """.trimIndent())
  }

  @Test
  fun `absent, On and Off survive a round trip`() {
    assertThat(serialize(AccessibilitySettingsState())).isNull()

    for (soundSignals in listOf(
      SoundSignalsSettingsState(playSignals = true, signals = mapOf("error.line" to true)),
      SoundSignalsSettingsState(playSignals = false, signals = mapOf("error.line" to false, "folded.line" to true)),
      SoundSignalsSettingsState(signals = mapOf("error.line" to false)),
    )) {
      val state = AccessibilitySettingsState(soundSignals)
      val element = serialize(state)!!
      assertThat(element.deserialize(AccessibilitySettingsState::class.java)).isEqualTo(state)
    }
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `the registry key overrides an explicit On`(): Unit = withSoundSignalsSettings { settings ->
    settings.setPlaySignals(true)

    assertThat(isSoundSignalsOn()).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()
  }
}
