// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilityStateCollector
import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** The sound signal configuration metrics, reported by `AccessibilityStateCollector` on the `accessibility.state` group. */
@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalsStateMetricsTest {
  @Test
  fun `nothing is reported while no signal has a choice`(): Unit = withSoundSignalsSettings {
    setSupportScreenReaders(true)

    assertThat(soundSignalEventIds()).isEmpty()
  }

  @Test
  fun `every explicit choice is reported by name`(): Unit = withSoundSignalsSettings { settings ->
    settings.setSignal(IdeSoundSignals.WARNING_CARET, false)
    settings.setSignal(IdeSoundSignals.FOLDED_CARET, false)
    settings.setSignal(IdeSoundSignals.ERROR_LINE, true)
    settings.setSignal("plugin.only.signal", false)

    assertThat(choices()).containsExactlyInAnyOrder("warning.caret" to false, "folded.caret" to false, "error.line" to true)
  }

  @Test
  @RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "false")
  fun `nothing is reported while the feature is off`(): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(true)
    settings.setSignal(IdeSoundSignals.FOLDED_LINE, false)

    assertThat(soundSignalEventIds()).isEmpty()
  }

  private fun collect(): Set<MetricEvent> =
    FUCollectorTestCase.collectApplicationStateCollectorEvents(AccessibilityStateCollector::class.java)

  private fun soundSignalEventIds(): List<String> = collect().map { it.eventId }.filter { it.startsWith("sound.signal") }

  private fun choices(): List<Pair<String?, Any?>> =
    collect().filter { it.eventId == "sound.signal.override" }.map { it.data.build().let { data -> data["signal"] as String? to data["enabled"] } }
}
