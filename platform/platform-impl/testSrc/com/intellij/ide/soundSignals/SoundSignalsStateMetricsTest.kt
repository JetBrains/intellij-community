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
  fun `the calculated play value is reported as not chosen`(): Unit = withSoundSignalsSettings {
    setSupportScreenReaders(false)
    assertThat(mode()).containsExactly(mapOf("enabled" to false, "explicit" to false))

    setSupportScreenReaders(true)
    assertThat(mode()).containsExactly(mapOf("enabled" to true, "explicit" to false))
  }

  @Test
  fun `an explicit play value is reported as chosen`(): Unit = withSoundSignalsSettings { settings ->
    setSupportScreenReaders(true)
    settings.setPlaySignals(false)
    assertThat(mode()).containsExactly(mapOf("enabled" to false, "explicit" to true))

    settings.setPlaySignals(true)
    assertThat(mode()).containsExactly(mapOf("enabled" to true, "explicit" to true))
  }

  @Test
  fun `nothing but the mode is reported while no signal is muted`(): Unit = withSoundSignalsSettings {
    assertThat(soundSignalEventIds()).containsExactly("sound.signals.mode")
  }

  @Test
  fun `every explicitly muted signal is reported by name`(): Unit = withSoundSignalsSettings { settings ->
    settings.setSignal(IdeSoundSignals.WARNING_CARET, false)
    settings.setSignal(IdeSoundSignals.FOLDED_CARET, false)
    settings.setSignal(IdeSoundSignals.ERROR_LINE, true)
    settings.setSignal("plugin.only.signal", false)

    assertThat(disabledSignals()).containsExactlyInAnyOrder("warning.caret", "folded.caret")
  }

  @Test
  fun `the muted set is reported even while play is off`(): Unit = withSoundSignalsSettings { settings ->
    settings.setPlaySignals(false)
    settings.setSignal(IdeSoundSignals.FOLDED_LINE, false)

    assertThat(disabledSignals()).containsExactly("folded.line")
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

  private fun mode(): List<Map<String, Any?>> =
    collect().filter { it.eventId == "sound.signals.mode" }.map { it.data.build().filterKeys { key -> key == "enabled" || key == "explicit" } }

  private fun disabledSignals(): List<String?> =
    collect().filter { it.eventId == "sound.signal.disabled" }.map { it.data.build()["signal"] as String? }
}
