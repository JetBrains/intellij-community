// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.accessibility.AccessibilityStateCollector
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The audio cue configuration metrics, reported by `AccessibilityStateCollector` on the `accessibility.state` group. */
@TestApplication
class AudioCuesStateMetricsTest {
  @Test
  fun `the mode is reported whatever it is, including its default`() {
    for (mode in AudioCuesMode.entries) {
      collectorTest { settings ->
        settings.setMode(mode)

        assertThat(modes()).containsExactly(mode.name)
      }
    }
  }

  @Test
  fun `nothing but the mode is reported while no cue is muted`() {
    collectorTest {
      assertThat(audioCueEventIds()).containsExactly("audio.cues.mode")
    }
  }

  @Test
  fun `every muted cue is reported by name`() {
    collectorTest { settings ->
      settings.setCueEnabled(IdeAudioCues.WARNING_CARET, false)
      settings.setCueEnabled(IdeAudioCues.FOLDED_CARET, false)

      assertThat(disabledCues()).containsExactlyInAnyOrder("warning.caret", "folded.caret")
    }
  }

  @Test
  fun `the muted set is reported even while the feature is switched off`() {
    collectorTest { settings ->
      settings.setMode(AudioCuesMode.OFF)
      settings.setCueEnabled(IdeAudioCues.FOLDED_LINE, false)

      assertThat(disabledCues()).containsExactly("folded.line")
    }
  }

  private fun collect(): Set<MetricEvent> =
    FUCollectorTestCase.collectApplicationStateCollectorEvents(AccessibilityStateCollector::class.java)

  private fun eventIds(): List<String> = collect().map { it.eventId }

  private fun audioCueEventIds(): List<String> = eventIds().filter { it.startsWith("audio.cue") }

  private fun modes(): List<String?> = valuesOf("audio.cues.mode", "mode").map { it as String? }

  private fun disabledCues(): List<String?> = valuesOf("audio.cue.disabled", "cue").map { it as String? }

  private fun valuesOf(eventId: String, field: String): List<Any?> =
    collect().filter { it.eventId == eventId }.map { it.data.build()[field] }

  private fun collectorTest(body: (AudioCuesSettings) -> Unit) = withAudioCuesSettings(body)
}
