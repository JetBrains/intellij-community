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
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()

    setSupportScreenReaders(true)
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isTrue()
  }

  @Test
  fun `an explicit signal choice wins over screen reader support`(): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(true)
    settings.setSignal(IdeSoundSignals.WARNING_LINE, false)
    assertThat(isSoundSignalOn(IdeSoundSignals.WARNING_LINE)).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isTrue()

    setSupportScreenReaders(false)
    settings.setSignal(IdeSoundSignals.FOLDED_LINE, true)
    assertThat(isSoundSignalOn(IdeSoundSignals.FOLDED_LINE)).isTrue()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()
  }

  @Test
  fun `the sound signals are stored in their own tag of the accessibility state`() {
    val state = AccessibilitySettingsState(SoundSignalsSettingsState(signals = mapOf("error.line" to false)))

    assertThat(JDOMUtil.write(serialize(state)!!)).isEqualTo("""
      <AccessibilitySettingsState>
        <soundSignals>
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
      SoundSignalsSettingsState(signals = mapOf("error.line" to true)),
      SoundSignalsSettingsState(signals = mapOf("error.line" to false, "folded.line" to true)),
    )) {
      val state = AccessibilitySettingsState(soundSignals)
      val element = serialize(state)!!
      assertThat(element.deserialize(AccessibilitySettingsState::class.java)).isEqualTo(state)
    }
  }

  @Test
  fun `a stored Play sound signals option is ignored`() {
    val element = JDOMUtil.load("""
      <AccessibilitySettingsState>
        <soundSignals>
          <option name="playSignals" value="true" />
          <signals>
            <signal id="error.line" enabled="false" />
          </signals>
        </soundSignals>
      </AccessibilitySettingsState>
    """.trimIndent())

    assertThat(element.deserialize(AccessibilitySettingsState::class.java))
      .isEqualTo(AccessibilitySettingsState(SoundSignalsSettingsState(signals = mapOf("error.line" to false))))
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `the registry key overrides an explicit On`(): Unit = withSoundSignalsSettings { settings ->
    settings.setSignal(IdeSoundSignals.ERROR_LINE, true)

    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()
  }
}
