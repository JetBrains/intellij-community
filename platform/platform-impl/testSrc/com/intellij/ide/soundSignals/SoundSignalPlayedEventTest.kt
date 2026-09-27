// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalPlayedEventTest {
  @TestDisposable
  private lateinit var disposable: Disposable

  @Test
  fun `an enabled signal is reported by name`() = collectorTest {
    val events = collect { SoundSignalPlayer.getInstance().play(IdeSoundSignals.ERROR_LINE) }

    assertThat(events).singleElement()
      .satisfies({
        assertThat(it.event.id).isEqualTo("sound.signal.played")
        assertThat(it.group.id).isEqualTo("accessibility")
        assertThat(it.event.data["signal"]).isEqualTo("error.line")
      })
  }

  @Test
  fun `every signal of a batch is reported, including two sharing one sound`() = collectorTest {
    val signals = listOf(IdeSoundSignals.ERROR_LINE, IdeSoundSignals.ERROR_CARET, IdeSoundSignals.FOLDED_LINE)
    val events = collect { SoundSignalPlayer.getInstance().play(*signals.toTypedArray()) }

    assertThat(events.map { it.event.data["signal"] })
      .containsExactlyInAnyOrder("error.line", "error.caret", "folded.line")
  }

  @Test
  fun `a signal repeated within one batch is reported once`() = collectorTest {
    val events = collect { SoundSignalPlayer.getInstance().play(IdeSoundSignals.ERROR_LINE, IdeSoundSignals.ERROR_LINE) }

    assertThat(events.map { it.event.data["signal"] }).containsExactly("error.line")
  }

  @Test
  fun `a muted signal is not reported`() = collectorTest { settings ->
    settings.setSignal(IdeSoundSignals.WARNING_LINE, false)

    val events = collect { SoundSignalPlayer.getInstance().play(IdeSoundSignals.WARNING_LINE, IdeSoundSignals.ERROR_LINE) }

    assertThat(events.map { it.event.data["signal"] }).containsExactly("error.line")
  }

  @Test
  fun `nothing is reported while the feature is off`() = collectorTest { settings ->
    settings.setPlaySignals(false)

    val events = collect { SoundSignalPlayer.getInstance().play(IdeSoundSignals.ERROR_LINE) }

    assertThat(events).isEmpty()
  }

  @Test
  fun `nothing is reported under the calculated default without screen reader support`(): Unit = withSoundSignalsSettings {
    ApplicationManager.getApplication().replaceService(SoundSignalPlayer::class.java, SilentPlayer(), disposable)
    setSupportScreenReaders(false)

    val events = collect { SoundSignalPlayer.getInstance().play(IdeSoundSignals.ERROR_LINE) }

    assertThat(events).isEmpty()
  }

  private fun collect(action: () -> Unit): List<LogEvent> =
    FUCollectorTestCase.collectLogEvents(disposable, action)
      .filter { it.group.id == "accessibility" && it.event.id == "sound.signal.played" }

  private fun collectorTest(body: (AccessibilitySettings) -> Unit) = withSoundSignalsSettings { settings ->
    settings.setPlaySignals(true)
    ApplicationManager.getApplication().replaceService(SoundSignalPlayer::class.java, SilentPlayer(), disposable)
    body(settings)
  }

  private class SilentPlayer : SoundSignalPlayer() {
    override fun playEnabled(signals: Collection<SoundSignal>) {}
  }
}
