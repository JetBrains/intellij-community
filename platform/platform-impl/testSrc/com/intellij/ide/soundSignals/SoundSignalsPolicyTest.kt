// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SoundSignalsPolicyTest {
  @Test
  fun `a signal without a choice follows screen reader support`() {
    for (screenReader in listOf(false, true)) {
      val policy = SoundSignalsPolicy(screenReader, SoundSignalsSettingsState())

      assertThat(policy.isSignalOn(ID)).isEqualTo(screenReader)
    }
  }

  @Test
  fun `explicit choices win over every calculated default`() {
    for (screenReader in listOf(false, true)) {
      for (value in listOf(false, true)) {
        val state = SoundSignalsSettingsState(signals = mapOf(ID to value))
        val policy = SoundSignalsPolicy(screenReader, state)

        assertThat(policy.isSignalOn(ID)).isEqualTo(value)
      }
    }
  }

  @Test
  fun `an explicit signal choice leaves the other signals inherited`() {
    for (screenReader in listOf(false, true)) {
      val policy = SoundSignalsPolicy(screenReader, SoundSignalsSettingsState(signals = mapOf(ID to !screenReader)))

      assertThat(policy.isSignalOn(ID)).isEqualTo(!screenReader)
      assertThat(policy.isSignalOn("other")).isEqualTo(screenReader)
    }
  }

  private companion object {
    const val ID = "error.line"
  }
}
