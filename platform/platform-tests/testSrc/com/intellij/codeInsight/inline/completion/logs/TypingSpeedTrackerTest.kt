// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.logs

import com.intellij.testFramework.UsefulTestCase
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal class TypingSpeedTrackerTest : UsefulTestCase() {
  var currentTime = System.currentTimeMillis()
  fun `test typing speed tracker`() {
    val typingSpeedTracker = TypingSpeedTracker()
    typingSpeedTracker.type(300, 600)
    typingSpeedTracker.assertTypingSpeedEquals(1.seconds, 600)
    typingSpeedTracker.assertTypingSpeedEquals(2.seconds, 600)
    currentTime += 200
    assertEquals(200L, typingSpeedTracker.getTimeSinceLastTyping(currentTime))
    currentTime += 800
    typingSpeedTracker.type(10, 200)
    typingSpeedTracker.assertTypingSpeedEquals(1.seconds, 220)
    typingSpeedTracker.assertTypingSpeedEquals(2.seconds, 295)
    typingSpeedTracker.type(100, 200)
    typingSpeedTracker.assertTypingSpeedEquals(1.seconds, 200)
    typingSpeedTracker.assertTypingSpeedEquals(2.seconds, 200)
  }

  private fun TypingSpeedTracker.type(n: Int, typingSpeed: Int /* Chars per minute */) {
    for (i in 1..<n) {
      typingOccurred(currentTime)
      currentTime += 60 * 1000 / typingSpeed
    }
    typingOccurred(currentTime)
  }

  private fun TypingSpeedTracker.assertTypingSpeedEquals(decayDuration: Duration, speed: Int) =
    assertEquals(speed, getTypingSpeed(decayDuration)?.roundToInt())
}
