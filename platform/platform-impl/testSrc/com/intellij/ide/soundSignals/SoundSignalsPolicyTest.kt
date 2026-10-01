// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SoundSignalsPolicyTest {
  @Test
  fun `a signal without a choice follows screen reader support`() {
    for (screenReader in listOf(false, true)) {
      val policy = policy(screenReader, SoundSignalsSettingsState())

      assertThat(policy.isSignalOn(ID)).isEqualTo(screenReader)
    }
  }

  @Test
  fun `explicit choices win over every calculated default`() {
    for (screenReader in listOf(false, true)) {
      for (value in listOf(false, true)) {
        val state = SoundSignalsSettingsState(signals = mapOf(ID to value))
        val policy = policy(screenReader, state)

        assertThat(policy.isSignalOn(ID)).isEqualTo(value)
      }
    }
  }

  @Test
  fun `an explicit signal choice leaves the other signals inherited`() {
    for (screenReader in listOf(false, true)) {
      val policy = policy(screenReader, SoundSignalsSettingsState(signals = mapOf(ID to !screenReader)))

      assertThat(policy.isSignalOn(ID)).isEqualTo(!screenReader)
      assertThat(policy.isSignalOn("other")).isEqualTo(screenReader)
    }
  }

  @Test
  fun `a legacy Play sound of a bound group plays only its signal`() {
    val policy = policy(screenReader = false, SoundSignalsSettingsState(), stored = setOf(BUILD))

    assertThat(policy.isSignalOn(BUILD)).isTrue()
    assertThat(policy.isSignalOn(TESTS)).isFalse()
    assertThat(policy.isSignalOn(ID)).isFalse()
  }

  @Test
  fun `an explicit Off of a bound signal replaces the legacy Play sound`() {
    val policy = policy(screenReader = true, SoundSignalsSettingsState(signals = mapOf(BUILD to false)), stored = setOf(BUILD))

    assertThat(policy.isSignalOn(BUILD)).isFalse()
    assertThat(policy.isSignalOn(ID)).isTrue()
  }

  private fun policy(screenReader: Boolean, state: SoundSignalsSettingsState, stored: Set<String> = emptySet()): SoundSignalsPolicy =
    SoundSignalsPolicy(screenReader, state, playSoundStored = { it in stored })

  private companion object {
    const val ID = "error.line"
    const val BUILD = "build.finished"
    const val TESTS = "test.results"
  }
}
