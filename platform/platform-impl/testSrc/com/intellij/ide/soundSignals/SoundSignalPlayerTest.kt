// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.openapi.components.service
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
@RegistryKey(key = SOUND_SIGNALS_ENABLED_REGISTRY_KEY, value = "true")
class SoundSignalPlayerTest {
  @Test
  fun `a preview plays the preview sounds of the signals in order`() = playerTest { player ->
    player.preview(TWO_SOUNDS, ONE_SOUND)

    assertThat(player.played).containsExactly(
      TWO_SOUNDS to IdeSounds.ERROR,
      TWO_SOUNDS to IdeSounds.WARNING,
      ONE_SOUND to IdeSounds.CODE_FOLDING,
    )
  }

  @Test
  fun `a preview plays only the declared preview sounds`() = playerTest { player ->
    player.preview(CUSTOM_PREVIEW)

    assertThat(player.played).containsExactly(CUSTOM_PREVIEW to IdeSounds.WARNING)
  }

  @Test
  fun `play plays the first sound of every enabled signal`() = playerTest { player ->
    player.play(IdeSoundSignals.ERROR_LINE, IdeSoundSignals.FOLDED_LINE)

    assertThat(player.played.map { it.second }).containsExactlyInAnyOrder(IdeSounds.ERROR, IdeSounds.CODE_FOLDING)
  }

  @Test
  fun `play plays the chosen sound`() = playerTest { player ->
    player.play(IdeSoundSignals.PROGRESS, IdeSounds.PROGRESS_DETERMINATE_STAGE_2)

    assertThat(player.played).containsExactly(IdeSoundSignals.PROGRESS to IdeSounds.PROGRESS_DETERMINATE_STAGE_2)
  }

  @Test
  fun `a muted signal plays nothing`() = playerTest { player ->
    service<AccessibilitySettings>().setSignal(IdeSoundSignals.PROGRESS, false)

    player.play(IdeSoundSignals.PROGRESS, IdeSounds.PROGRESS_DETERMINATE_STAGE_2)
    player.play(IdeSoundSignals.PROGRESS)

    assertThat(player.played).isEmpty()
  }

  @Test
  fun `a signal without sounds plays nothing`() = playerTest { player ->
    player.play(NO_SOUND)
    player.preview(NO_SOUND)

    assertThat(player.played).isEmpty()
  }

  private fun playerTest(body: suspend (RecordingPlayer) -> Unit): Unit = timeoutRunBlocking(30.seconds) {
    withSoundSignalsSettings { settings ->
      setSupportScreenReaders(true)
      settings.loadSoundSignals(SoundSignalsSettingsState())
      body(RecordingPlayer())
    }
  }

  private class RecordingPlayer : SoundSignalPlayer() {
    val played = ArrayList<Pair<SoundSignal, String>>()

    override fun playEnabled(sounds: Map<SoundSignal, String>) {
      played += sounds.toList()
    }

    override fun playPreview(sounds: List<Pair<SoundSignal, String>>) {
      played += sounds
    }
  }

  private companion object {
    val TWO_SOUNDS = SoundSignal("test.two.sounds", { "Two sounds" }, listOf(IdeSounds.ERROR, IdeSounds.WARNING), IdeSoundSignals::class.java, 0)
    val CUSTOM_PREVIEW = SoundSignal(
      "test.custom.preview", { "Custom preview" }, listOf(IdeSounds.ERROR, IdeSounds.WARNING), IdeSoundSignals::class.java, 0,
      preview = listOf(IdeSounds.WARNING),
    )
    val ONE_SOUND =SoundSignal("test.one.sound", { "One sound" }, IdeSounds.CODE_FOLDING, IdeSoundSignals::class.java, 0)
    val NO_SOUND = SoundSignal("test.no.sound", { "No sound" }, emptyList(), IdeSoundSignals::class.java, 0)
  }
}
