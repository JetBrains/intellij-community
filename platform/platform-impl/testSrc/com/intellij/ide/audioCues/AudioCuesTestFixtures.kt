// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.audioCues

import com.intellij.openapi.components.service

internal inline fun <T> withAudioCuesSettings(body: (AudioCuesSettings) -> T): T {
  val settings = service<AudioCuesSettings>()
  val stateBefore = settings.state
  try {
    return body(settings)
  }
  finally {
    settings.loadState(stateBefore)
  }
}
