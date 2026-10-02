// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class PendingSoundSignalsTest {
  private val mutedChoices = mapOf("error.line" to false)
  private val muted = SoundSignalsSettingsState(signals = mutedChoices)

  @Test
  fun `an edit of one page leaves the other page and the applied settings unchanged`() = pendingTest { settings ->
    val accessibility = PendingSoundSignals { true }
    val notifications = PendingSoundSignals { true }
    notifications.choose(mutedChoices)

    assertThat(notifications.isModified()).isTrue()
    assertThat(notifications.policy().isSignalOn("error.line")).isFalse()
    assertThat(accessibility.isModified()).isFalse()
    assertThat(accessibility.policy().isSignalOn("error.line")).isTrue()
    assertThat(settings.soundSignals).isEqualTo(SoundSignalsSettingsState())
  }

  @Test
  fun `a choice that another page has applied is no longer a modification`() = pendingTest { settings ->
    val notifications = PendingSoundSignals()
    notifications.choose(mutedChoices)

    settings.loadSoundSignals(muted)

    assertThat(notifications.isModified()).isFalse()
  }

  @Test
  fun `an apply without a choice writes nothing`() = pendingTest { settings ->
    val pending = PendingSoundSignals()
    pending.choose(mutedChoices)
    pending.apply()
    assertThat(settings.soundSignals).isEqualTo(muted)
    assertThat(pending.isModified()).isFalse()

    val other = SoundSignalsSettingsState(signals = mapOf("folded.line" to true))
    settings.loadSoundSignals(other)
    pending.apply()
    assertThat(settings.soundSignals).isEqualTo(other)
  }

  @Test
  fun `a reset drops the choices and renders the page`() = pendingTest {
    val pending = PendingSoundSignals { true }
    val rendered = ArrayList<Boolean>()
    pending.view { rendered += it.isSignalOn("error.line") }

    pending.choose(mutedChoices)
    pending.reset()

    assertThat(rendered).containsExactly(false, true)
    assertThat(pending.isModified()).isFalse()
  }

  @Test
  fun `the page provides the screen reader value`() = pendingTest {
    setSupportScreenReaders(false)

    assertThat(PendingSoundSignals { true }.policy().isSignalOn("folded.line")).isTrue()
    assertThat(PendingSoundSignals().policy().isSignalOn("folded.line")).isFalse()
  }

  private fun pendingTest(body: (AccessibilitySettings) -> Unit): Unit = withSoundSignalsSettings { settings ->
    settings.loadSoundSignals(SoundSignalsSettingsState())
    body(settings)
  }
}
