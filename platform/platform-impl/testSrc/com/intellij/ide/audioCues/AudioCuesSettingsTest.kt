// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.ui.accessibility.ScreenReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = AUDIO_CUES_ENABLED_REGISTRY_KEY, value = "true")
class AudioCuesSettingsTest {
  @Test
  fun `the explicit modes ignore the screen reader`() = settingsTest { settings ->
    for (active in listOf(false, true)) {
      ScreenReader.setActive(active)

      settings.setMode(AudioCuesMode.ON)
      assertThat(settings.isEnabled).isTrue()

      settings.setMode(AudioCuesMode.OFF)
      assertThat(settings.isEnabled).isFalse()
    }
  }

  @Test
  fun `AUTO follows the screen reader`() = settingsTest { settings ->
    settings.setMode(AudioCuesMode.AUTO)

    ScreenReader.setActive(false)
    assertThat(settings.isEnabled).isFalse()

    ScreenReader.setActive(true)
    assertThat(settings.isEnabled).isTrue()

    ScreenReader.setActive(false)
    assertThat(settings.isEnabled).isFalse()
  }

  @Test
  fun `a muted cue stays muted while the mode is on`() = settingsTest { settings ->
    settings.setMode(AudioCuesMode.ON)
    settings.setCueEnabled(IdeAudioCues.WARNING_LINE, false)

    assertThat(settings.isCueEnabled(IdeAudioCues.WARNING_LINE)).isFalse()
    assertThat(settings.isCueEnabled(IdeAudioCues.ERROR_LINE)).isTrue()
  }

  @Test
  fun `a muted id with no declaration survives a round trip`() = settingsTest { settings ->
    settings.loadState(AudioCuesSettingsState(disabledCues = setOf("plugin.only.cue")))

    settings.setCueEnabled(IdeAudioCues.ERROR_LINE, false)
    assertThat(settings.state.disabledCues).containsExactlyInAnyOrder("plugin.only.cue", "error.line")

    settings.setCueEnabled(IdeAudioCues.ERROR_LINE, true)
    assertThat(settings.state.disabledCues).containsExactly("plugin.only.cue")
  }

  @Test
  @RegistryKey(key = AUDIO_CUES_ENABLED_REGISTRY_KEY, value = "false")
  fun `the registry key overrides the ON mode`() = settingsTest { settings ->
    settings.setMode(AudioCuesMode.ON)

    assertThat(settings.isEnabled).isFalse()
    assertThat(settings.isCueEnabled(IdeAudioCues.ERROR_LINE)).isFalse()
  }

  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  private fun settingsTest(body: (AudioCuesSettings) -> Unit) {
    val screenReaderBefore = ScreenReader.isActive()
    try {
      withAudioCuesSettings(body)
    }
    finally {
      ScreenReader.setActive(screenReaderBefore)
    }
  }
}
