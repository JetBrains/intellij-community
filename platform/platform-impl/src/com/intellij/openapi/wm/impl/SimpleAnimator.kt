// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal abstract class SimpleAnimator {
  // frame - in 1..totalFrames
  abstract fun paintNow(frame: Int, totalFrames: Int)

  protected open fun paintCycleStart() {}

  suspend fun run(totalFrames: Int, cycle: Duration) {
    val cycleDuration = cycle.inWholeNanoseconds
    val duration = (cycleDuration / totalFrames).toDuration(DurationUnit.NANOSECONDS)
    withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      paintCycleStart()
      val startTime = System.nanoTime()
      var currentFrame = -1
      while (true) {
        val cycleTime = System.nanoTime() - startTime
        if (cycleTime < 0) {
          break
        }

        val newFrame = ((cycleTime * totalFrames) / cycleDuration).toInt()
        if (newFrame == currentFrame) {
          continue
        }

        if (newFrame >= totalFrames) {
          break
        }

        currentFrame = newFrame
        paintNow(currentFrame, totalFrames)

        delay(duration)
      }
    }
  }
}
