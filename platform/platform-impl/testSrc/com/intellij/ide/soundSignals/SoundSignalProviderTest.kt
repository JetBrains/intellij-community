// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.internal.statistic.eventLog.validator.rules.EventContext
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationSoundEP
import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.jetbrains.fus.reporting.api.ValidationResultType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class SoundSignalProviderTest {
  @Test
  fun `the IDE provider is registered`() {
    assertThat(SoundSignalProvider.EP_NAME.extensionList).anyMatch { it is IdeSoundSignalProvider }
  }

  @Test
  fun `every IDE signal has a title and a readable sound`() {
    val signals = IdeSoundSignalProvider().soundSignals

    assertThat(signals).isNotEmpty()
    for (signal in signals) {
      assertThat(signal.title).describedAs("title of '%s'", signal.id).isNotBlank()
      for (path in signal.sounds) {
        val sound = signal.ownerClass.classLoader.getResourceAsStream(path)
        assertThat(sound).describedAs("sound '%s' of '%s'", path, signal.id).isNotNull()
        assertThat(sound!!.use { it.read() }).describedAs("sound '%s' of '%s'", path, signal.id).isNotEqualTo(-1)
      }
    }
  }

  @Test
  fun `every notification type has a readable sound`() {
    for (type in NotificationType.entries) {
      val sound = IdeSounds::class.java.classLoader.getResourceAsStream(notificationSound(type))
      assertThat(sound).describedAs("sound of %s", type).isNotNull()
      assertThat(sound!!.use { it.read() }).describedAs("sound of %s", type).isNotEqualTo(-1)
    }
  }

  @Test
  fun `the IDE signals keep their settings order`() {
    val ideIds = IdeSoundSignalProvider().soundSignals.map { it.id }.toSet()

    assertThat(getSoundSignals().map { it.id }.filter { it in ideIds })
      .containsExactly("build.finished", "test.results", "progress", "error.line", "error.caret", "warning.line", "warning.caret", "folded.line", "folded.caret")
  }

  @Test
  fun `every notification group binding names a registered signal and every group has one binding`() {
    val bindings = NotificationSoundEP.EP_NAME.extensionList

    assertThat(bindings.mapNotNull { it.soundSignal }).isNotEmpty()
    assertThat(bindings.mapNotNull { it.soundSignal }).allMatch { findSoundSignal(it) != null }
    assertThat(bindings.groupingBy { it.group }.eachCount().filterValues { it > 1 }).isEmpty()
  }

  @Test
  fun `two signals can share one sound`() {
    assertThat(IdeSoundSignals.ERROR_LINE.sounds).isEqualTo(IdeSoundSignals.ERROR_CARET.sounds)
  }

  @Test
  fun `an unknown id resolves to no signal`() {
    assertThat(findSoundSignal("no.such.signal")).isNull()
  }

  @Test
  fun `the FUS rule accepts an IDE signal`() {
    val context = EventContext.create("sound.signal.played", mapOf("signal" to "error.line"))

    assertThat(SoundSignalIdValidationRule().validate("error.line", context)).isEqualTo(ValidationResultType.ACCEPTED)
  }

  @Test
  fun `the FUS rule rejects an unknown signal`() {
    val context = EventContext.create("sound.signal.played", mapOf("signal" to "no.such.signal"))

    assertThat(SoundSignalIdValidationRule().validate("no.such.signal", context)).isEqualTo(ValidationResultType.REJECTED)
  }

  @Test
  fun `the FUS rule does not accept a third-party signal`(@TestDisposable disposable: Disposable) {
    // The signal takes a platform owner class on purpose. Only the registration tells the real owner.
    val signal = SoundSignal("third.party.signal", { "Third party" }, "sounds/none.wav", IdeSoundSignals::class.java, 0)
    val provider = object : SoundSignalProvider {
      override val soundSignals: Collection<SoundSignal> = listOf(signal)
    }
    SoundSignalProvider.EP_NAME.point.registerExtension(provider, DefaultPluginDescriptor("com.example.soundSignals"), disposable)
    val context = EventContext.create("sound.signal.played", mapOf("signal" to "third.party.signal"))

    assertThat(SoundSignalIdValidationRule().validate("third.party.signal", context)).isEqualTo(ValidationResultType.THIRD_PARTY)
  }
}
