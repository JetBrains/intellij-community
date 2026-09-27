// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SoundSignalsPolicyTest {
  @Test
  fun `play follows screen reader support and signals are on`() {
    for (screenReader in listOf(false, true)) {
      val policy = SoundSignalsPolicy(screenReader, SoundSignalsSettingsState())

      assertThat(policy.isPlaySignalsOn).isEqualTo(screenReader)
      assertThat(policy.isSignalOn(ID)).isTrue()
    }
  }

  @Test
  fun `explicit choices win over every calculated default`() {
    for (screenReader in listOf(false, true)) {
      for (value in listOf(false, true)) {
        val state = SoundSignalsSettingsState(playSignals = value, signals = mapOf(ID to value))
        val policy = SoundSignalsPolicy(screenReader, state)

        assertThat(policy.isPlaySignalsOn).isEqualTo(value)
        assertThat(policy.isSignalOn(ID)).isEqualTo(value)
      }
    }
  }

  @Test
  fun `an explicit signal choice leaves the other signals inherited`() {
    val policy = SoundSignalsPolicy(supportScreenReaders = false, SoundSignalsSettingsState(signals = mapOf(ID to false)))

    assertThat(policy.isSignalOn(ID)).isFalse()
    assertThat(policy.isSignalOn("other")).isTrue()
  }

  private companion object {
    const val ID = "error.line"
  }
}
