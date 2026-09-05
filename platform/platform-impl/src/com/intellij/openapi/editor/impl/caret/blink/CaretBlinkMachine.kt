// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import com.intellij.openapi.editor.impl.caret.model.CaretTick

internal class CaretBlinkMachine private constructor(private val phase: CaretBlinkPhase) {
  fun start(): CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Awake)

  fun stop(): CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Dormant)

  fun restart(): CaretBlinkMachine =
    CaretBlinkMachine(if (phase == CaretBlinkPhase.Dormant) phase else CaretBlinkPhase.Awake)

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretBlinkMachine, CaretBlinkStep> {
    val advancedPhase = phase.advance(tick)
    return CaretBlinkMachine(advancedPhase) to advancedPhase.step(tick, prefetching)
  }

  companion object {
    val DORMANT: CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Dormant)
  }
}
