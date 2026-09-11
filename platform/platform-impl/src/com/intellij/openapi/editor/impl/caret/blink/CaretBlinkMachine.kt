// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.caret.blink

import com.intellij.openapi.editor.impl.caret.model.CaretTick

internal class CaretBlinkMachine private constructor(private val phase: CaretBlinkPhase) {
  fun start(): CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Awake)

  fun stop(): CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Dormant)

  /**
   * Restarts the blink from a fully opaque caret, unless blinking is stopped altogether.
   */
  fun restart(): CaretBlinkMachine {
    val isStopped = phase == CaretBlinkPhase.Dormant
    val restartedPhase = if (isStopped) phase else CaretBlinkPhase.Awake
    return CaretBlinkMachine(restartedPhase)
  }

  fun advance(tick: CaretTick, prefetching: Boolean): Pair<CaretBlinkMachine, CaretBlinkStep> {
    val advancedPhase = phase.advance(tick)
    val next = CaretBlinkMachine(advancedPhase)
    val step = advancedPhase.step(tick, prefetching)
    return next to step
  }

  companion object {
    val DORMANT: CaretBlinkMachine = CaretBlinkMachine(CaretBlinkPhase.Dormant)
  }
}
