// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.accessibility.AccessibilitySettingsState
import com.intellij.configurationStore.serialize
import com.intellij.openapi.util.JDOMUtil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.accessibility.ScreenReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalsSettingsTest {
  @Test
  fun `the explicit modes ignore the screen reader`() = settingsTest { settings ->
    for (active in listOf(false, true)) {
      ScreenReader.setActive(active)

      settings.setMode(SoundSignalsMode.ON)
      assertThat(isSoundSignalsOn()).isTrue()

      settings.setMode(SoundSignalsMode.OFF)
      assertThat(isSoundSignalsOn()).isFalse()
    }
  }

  @Test
  fun `AUTO follows the screen reader`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.AUTO)

    ScreenReader.setActive(false)
    assertThat(isSoundSignalsOn()).isFalse()

    ScreenReader.setActive(true)
    assertThat(isSoundSignalsOn()).isTrue()

    ScreenReader.setActive(false)
    assertThat(isSoundSignalsOn()).isFalse()
  }

  @Test
  fun `a muted signal stays muted while the mode is on`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.ON)
    settings.setSignalEnabled(IdeSoundSignals.WARNING_LINE, false)

    assertThat(isSoundSignalOn(IdeSoundSignals.WARNING_LINE)).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isTrue()
  }

  @Test
  fun `a muted id with no declaration survives a round trip`() = settingsTest { settings ->
    settings.loadSoundSignals(SoundSignalsSettingsState(disabledSignals = setOf("plugin.only.signal")))

    settings.setSignalEnabled(IdeSoundSignals.ERROR_LINE, false)
    assertThat(settings.soundSignals.disabledSignals).containsExactlyInAnyOrder("plugin.only.signal", "error.line")

    settings.setSignalEnabled(IdeSoundSignals.ERROR_LINE, true)
    assertThat(settings.soundSignals.disabledSignals).containsExactly("plugin.only.signal")
  }

  @Test
  fun `the sound signals are stored in their own tag of the accessibility state`() {
    val state = AccessibilitySettingsState(SoundSignalsSettingsState(mode = SoundSignalsMode.ON, disabledSignals = setOf("error.line")))

    assertThat(JDOMUtil.write(serialize(state)!!)).isEqualTo("""
      <AccessibilitySettingsState>
        <soundSignals>
          <option name="mode" value="ON" />
          <option name="disabledSignals">
            <set>
              <option value="error.line" />
            </set>
          </option>
        </soundSignals>
      </AccessibilitySettingsState>
    """.trimIndent())
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `the registry key overrides the ON mode`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.ON)

    assertThat(isSoundSignalsOn()).isFalse()
    assertThat(isSoundSignalOn(IdeSoundSignals.ERROR_LINE)).isFalse()
  }

  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  private fun settingsTest(body: (AccessibilitySettings) -> Unit) {
    val screenReaderBefore = ScreenReader.isActive()
    try {
      withSoundSignalsSettings(body)
    }
    finally {
      ScreenReader.setActive(screenReaderBefore)
    }
  }
}
