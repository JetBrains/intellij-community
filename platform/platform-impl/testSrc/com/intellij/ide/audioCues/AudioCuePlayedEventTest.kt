// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.util.ui.accessibility.ScreenReader
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = AUDIO_CUES_ENABLED_REGISTRY_KEY, value = "true")
class AudioCuePlayedEventTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `an enabled cue is reported by name`() = collectorTest {
    val events = collect { AudioCuePlayer.getInstance().play(IdeAudioCues.ERROR_LINE) }

    assertThat(events).singleElement()
      .satisfies({
        assertThat(it.event.id).isEqualTo("audio.cue.played")
        assertThat(it.group.id).isEqualTo("accessibility")
        assertThat(it.event.data["cue"]).isEqualTo("error.line")
      })
  }

  @Test
  fun `every cue of a batch is reported, including two sharing one sound`() = collectorTest {
    val cues = listOf(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_CARET, IdeAudioCues.FOLDED_LINE)
    val events = collect { AudioCuePlayer.getInstance().play(*cues.toTypedArray()) }

    assertThat(events.map { it.event.data["cue"] })
      .containsExactlyInAnyOrder("error.line", "error.caret", "folded.line")
  }

  @Test
  fun `a cue repeated within one batch is reported once`() = collectorTest {
    val events = collect { AudioCuePlayer.getInstance().play(IdeAudioCues.ERROR_LINE, IdeAudioCues.ERROR_LINE) }

    assertThat(events.map { it.event.data["cue"] }).containsExactly("error.line")
  }

  @Test
  fun `a muted cue is not reported`() = collectorTest { settings ->
    settings.setCueEnabled(IdeAudioCues.WARNING_LINE, false)

    val events = collect { AudioCuePlayer.getInstance().play(IdeAudioCues.WARNING_LINE, IdeAudioCues.ERROR_LINE) }

    assertThat(events.map { it.event.data["cue"] }).containsExactly("error.line")
  }

  @Test
  fun `nothing is reported while the feature is off`() = collectorTest { settings ->
    settings.setMode(AudioCuesMode.OFF)

    val events = collect { AudioCuePlayer.getInstance().play(IdeAudioCues.ERROR_LINE) }

    assertThat(events).isEmpty()
  }

  /** [ScreenReader.setActive] is a process-wide static with no restore API, so its prior value is saved by hand. */
  @Test
  fun `nothing is reported in AUTO without a screen reader`() = collectorTest { settings ->
    val screenReaderBefore = ScreenReader.isActive()
    try {
      ScreenReader.setActive(false)
      settings.setMode(AudioCuesMode.AUTO)

      val events = collect { AudioCuePlayer.getInstance().play(IdeAudioCues.ERROR_LINE) }

      assertThat(events).isEmpty()
    }
    finally {
      ScreenReader.setActive(screenReaderBefore)
    }
  }

  private fun collect(action: () -> Unit): List<LogEvent> =
    FUCollectorTestCase.collectLogEvents(disposable, action)
      .filter { it.group.id == "accessibility" && it.event.id == "audio.cue.played" }

  private fun collectorTest(body: (AudioCuesSettings) -> Unit) = withAudioCuesSettings { settings ->
    settings.setMode(AudioCuesMode.ON)
    ApplicationManager.getApplication().replaceService(AudioCuePlayer::class.java, SilentPlayer(), disposable)
    body(settings)
  }

  private class SilentPlayer : AudioCuePlayer() {
    override fun playEnabled(cues: Collection<AudioCue>) {}
  }
}
