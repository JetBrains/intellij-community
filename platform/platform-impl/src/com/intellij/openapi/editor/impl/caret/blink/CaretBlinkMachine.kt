// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import com.intellij.openapi.editor.impl.caret.model.CaretTick

internal class CaretBlinkMachine {
  private var phase: CaretBlinkPhase = CaretBlinkPhase.Dormant

  fun start() {
    phase = CaretBlinkPhase.Awake
  }

  fun stop() {
    phase = CaretBlinkPhase.Dormant
  }

  fun restart() {
    phase = if (phase == CaretBlinkPhase.Dormant) phase else CaretBlinkPhase.Awake
  }

  fun advance(tick: CaretTick, prefetching: Boolean): CaretBlinkFrame {
    phase = phase.advance(tick)
    return phase.frame(tick, prefetching)
  }
}
