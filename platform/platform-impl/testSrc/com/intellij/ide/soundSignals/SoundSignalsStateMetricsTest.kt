// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.accessibility.AccessibilityStateCollector
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The sound signal configuration metrics, reported by `AccessibilityStateCollector` on the `accessibility.state` group. */
@TestApplication
class SoundSignalsStateMetricsTest {
  @Test
  fun `the mode is reported whatever it is, including its default`() {
    for (mode in SoundSignalsMode.entries) {
      collectorTest { settings ->
        settings.setMode(mode)

        assertThat(modes()).containsExactly(mode.name)
      }
    }
  }

  @Test
  fun `nothing but the mode is reported while no signal is muted`() {
    collectorTest {
      assertThat(soundSignalEventIds()).containsExactly("sound.signals.mode")
    }
  }

  @Test
  fun `every muted signal is reported by name`() {
    collectorTest { settings ->
      settings.setSignalEnabled(IdeSoundSignals.WARNING_CARET, false)
      settings.setSignalEnabled(IdeSoundSignals.FOLDED_CARET, false)

      assertThat(disabledSignals()).containsExactlyInAnyOrder("warning.caret", "folded.caret")
    }
  }

  @Test
  fun `the muted set is reported even while the feature is switched off`() {
    collectorTest { settings ->
      settings.setMode(SoundSignalsMode.OFF)
      settings.setSignalEnabled(IdeSoundSignals.FOLDED_LINE, false)

      assertThat(disabledSignals()).containsExactly("folded.line")
    }
  }

  private fun collect(): Set<MetricEvent> =
    FUCollectorTestCase.collectApplicationStateCollectorEvents(AccessibilityStateCollector::class.java)

  private fun eventIds(): List<String> = collect().map { it.eventId }

  private fun soundSignalEventIds(): List<String> = eventIds().filter { it.startsWith("sound.signal") }

  private fun modes(): List<String?> = valuesOf("sound.signals.mode", "mode").map { it as String? }

  private fun disabledSignals(): List<String?> = valuesOf("sound.signal.disabled", "signal").map { it as String? }

  private fun valuesOf(eventId: String, field: String): List<Any?> =
    collect().filter { it.eventId == eventId }.map { it.data.build()[field] }

  private fun collectorTest(body: (AccessibilitySettings) -> Unit) = withSoundSignalsSettings(body)
}
