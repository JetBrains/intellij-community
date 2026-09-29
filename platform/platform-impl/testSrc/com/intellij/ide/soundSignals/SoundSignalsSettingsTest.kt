// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

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
      assertThat(settings.isEnabled).isTrue()

      settings.setMode(SoundSignalsMode.OFF)
      assertThat(settings.isEnabled).isFalse()
    }
  }

  @Test
  fun `AUTO follows the screen reader`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.AUTO)

    ScreenReader.setActive(false)
    assertThat(settings.isEnabled).isFalse()

    ScreenReader.setActive(true)
    assertThat(settings.isEnabled).isTrue()

    ScreenReader.setActive(false)
    assertThat(settings.isEnabled).isFalse()
  }

  @Test
  fun `a muted signal stays muted while the mode is on`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.ON)
    settings.setSignalEnabled(IdeSoundSignals.WARNING_LINE, false)

    assertThat(settings.isSignalEnabled(IdeSoundSignals.WARNING_LINE)).isFalse()
    assertThat(settings.isSignalEnabled(IdeSoundSignals.ERROR_LINE)).isTrue()
  }

  @Test
  fun `a muted id with no declaration survives a round trip`() = settingsTest { settings ->
    settings.loadState(SoundSignalsSettingsState(disabledSignals = setOf("plugin.only.signal")))

    settings.setSignalEnabled(IdeSoundSignals.ERROR_LINE, false)
    assertThat(settings.state.disabledSignals).containsExactlyInAnyOrder("plugin.only.signal", "error.line")

    settings.setSignalEnabled(IdeSoundSignals.ERROR_LINE, true)
    assertThat(settings.state.disabledSignals).containsExactly("plugin.only.signal")
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `the registry key overrides the ON mode`() = settingsTest { settings ->
    settings.setMode(SoundSignalsMode.ON)

    assertThat(settings.isEnabled).isFalse()
    assertThat(settings.isSignalEnabled(IdeSoundSignals.ERROR_LINE)).isFalse()
  }

  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  private fun settingsTest(body: (SoundSignalsSettings) -> Unit) {
    val screenReaderBefore = ScreenReader.isActive()
    try {
      withSoundSignalsSettings(body)
    }
    finally {
      ScreenReader.setActive(screenReaderBefore)
    }
  }
}
