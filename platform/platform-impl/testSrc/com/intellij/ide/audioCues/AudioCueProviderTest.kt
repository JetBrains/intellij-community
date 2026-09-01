// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AudioCueProviderTest {
  @Test
  fun `the IDE provider is registered`() {
    assertThat(AudioCueProvider.EP_NAME.extensionList).anyMatch { it is IdeAudioCueProvider }
  }

  @Test
  fun `every IDE cue has a title and a readable sound`() {
    val cues = IdeAudioCueProvider().audioCues

    assertThat(cues).isNotEmpty()
    for (cue in cues) {
      assertThat(cue.title).describedAs("title of '%s'", cue.id).isNotBlank()
      val sound = cue.ownerClass.classLoader.getResourceAsStream(cue.resourcePath)
      assertThat(sound).describedAs("sound of '%s'", cue.id).isNotNull()
      assertThat(sound!!.use { it.read() }).describedAs("sound of '%s'", cue.id).isNotEqualTo(-1)
    }
  }

  @Test
  fun `the IDE cues keep their settings order`() {
    val ideIds = IdeAudioCueProvider().audioCues.map { it.id }.toSet()

    assertThat(getAudioCues().map { it.id }.filter { it in ideIds })
      .containsExactly("error.line", "error.caret", "warning.line", "warning.caret", "folded.line", "folded.caret")
  }

  @Test
  fun `two cues can share one sound`() {
    assertThat(IdeAudioCues.ERROR_LINE.resourcePath).isEqualTo(IdeAudioCues.ERROR_CARET.resourcePath)
  }

  @Test
  fun `an unknown id resolves to no cue`() {
    assertThat(findAudioCue("no.such.cue")).isNull()
  }

  @Test
  fun `the FUS rule accepts an IDE cue`() {
    val context = EventContext.create("audio.cue.played", mapOf("cue" to "error.line"))

    assertThat(AudioCueIdValidationRule().validate("error.line", context)).isEqualTo(ValidationResultType.ACCEPTED)
  }

  @Test
  fun `the FUS rule rejects an unknown cue`() {
    val context = EventContext.create("audio.cue.played", mapOf("cue" to "no.such.cue"))

    assertThat(AudioCueIdValidationRule().validate("no.such.cue", context)).isEqualTo(ValidationResultType.REJECTED)
  }

  @Test
  fun `the FUS rule does not accept a third-party cue`(@TestDisposable disposable: Disposable) {
    // The cue takes a platform owner class on purpose. Only the registration tells the real owner.
    val cue = AudioCue("third.party.cue", { "Third party" }, "sounds/none.wav", IdeAudioCues::class.java, 0)
    val provider = object : AudioCueProvider {
      override val audioCues: Collection<AudioCue> = listOf(cue)
    }
    AudioCueProvider.EP_NAME.point.registerExtension(provider, DefaultPluginDescriptor("com.example.audioCues"), disposable)
    val context = EventContext.create("audio.cue.played", mapOf("cue" to "third.party.cue"))

    assertThat(AudioCueIdValidationRule().validate("third.party.cue", context)).isEqualTo(ValidationResultType.THIRD_PARTY)
  }
}
