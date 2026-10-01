// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.components.service

/** Also restores the Support screen readers setting, which the calculated defaults read. */
internal inline fun <T> withSoundSignalsSettings(body: (AccessibilitySettings) -> T): T {
  val settings = service<AccessibilitySettings>()
  val stateBefore = settings.state
  val generalSettings = GeneralSettings.getInstance()
  val screenReaderBefore = generalSettings.isSupportScreenReaders
  try {
    return body(settings)
  }
  finally {
    generalSettings.isSupportScreenReaders = screenReaderBefore
    settings.loadState(stateBefore)
  }
}

internal fun setSupportScreenReaders(enabled: Boolean) {
  GeneralSettings.getInstance().isSupportScreenReaders = enabled
}

internal val AccessibilitySettings.soundSignals: SoundSignalsSettingsState
  get() = state.soundSignals

internal fun AccessibilitySettings.loadSoundSignals(state: SoundSignalsSettingsState) {
  loadState(this.state.copy(soundSignals = state))
}

internal fun AccessibilitySettings.setSignal(id: String, enabled: Boolean) {
  update { it.copy(soundSignals = it.soundSignals.copy(signals = it.soundSignals.signals + (id to enabled))) }
}

internal fun AccessibilitySettings.setSignal(signal: SoundSignal, enabled: Boolean) {
  setSignal(signal.id, enabled)
}
