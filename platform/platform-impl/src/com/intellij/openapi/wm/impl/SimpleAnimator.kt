// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.RemoteDesktopService
import com.intellij.openapi.application.EDT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// frame - in 1..totalFrames
suspend fun animate(totalFrames: Int, cycleDuration: Long, painter: (frame: Int, totalFrames: Int) -> Unit) {
  delay(cycleDuration / totalFrames)

  withContext(Dispatchers.EDT) {
    val startTime = System.currentTimeMillis()
    var currentFrame = 1
    painter(currentFrame, totalFrames)

    while (true) {
      delay(cycleDuration / totalFrames)

      val cycleTime = System.currentTimeMillis() - startTime
      // currentTimeMillis() is not monotonic - let's pretend that animation didn't change
      if (cycleTime < 0) {
        continue
      }

      val newFrame = ((cycleTime * totalFrames) / cycleDuration).toInt().coerceAtMost(totalFrames)
      if (newFrame == currentFrame) {
        continue
      }

      currentFrame = newFrame
      painter(currentFrame, totalFrames)

      if (newFrame == totalFrames) {
        break
      }
    }
  }
}

suspend fun fadeOut(totalFrames: Int = 12,
                    cycleDuration: Long = if (RemoteDesktopService.isRemoteSession()) 2_520 else 504,
                    initialAlpha: Float = 1f,
                    painter: (alpha: Float) -> Unit) {
  val opacityPerFrame = initialAlpha / totalFrames
  animate(totalFrames = totalFrames, cycleDuration = cycleDuration) { frame, _ ->
    painter(initialAlpha - (frame * opacityPerFrame))
  }
}