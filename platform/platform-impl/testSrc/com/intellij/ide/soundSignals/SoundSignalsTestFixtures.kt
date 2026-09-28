// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.soundSignals

import com.intellij.accessibility.AccessibilitySettings
import com.intellij.openapi.components.service

internal inline fun <T> withSoundSignalsSettings(body: (AccessibilitySettings) -> T): T {
  val settings = service<AccessibilitySettings>()
  val stateBefore = settings.state
  try {
    return body(settings)
  }
  finally {
    settings.loadState(stateBefore)
  }
}

internal val AccessibilitySettings.soundSignals: SoundSignalsSettingsState
  get() = state.soundSignals

internal fun AccessibilitySettings.loadSoundSignals(state: SoundSignalsSettingsState) {
  loadState(this.state.copy(soundSignals = state))
}

internal fun AccessibilitySettings.setMode(mode: SoundSignalsMode) {
  update { it.copy(soundSignals = it.soundSignals.copy(mode = mode)) }
}

internal fun AccessibilitySettings.setSignalEnabled(signal: SoundSignal, enabled: Boolean) {
  update {
    val disabled = it.soundSignals.disabledSignals
    it.copy(soundSignals = it.soundSignals.copy(disabledSignals = if (enabled) disabled - signal.id else disabled + signal.id))
  }
}
