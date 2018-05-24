/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide

import org.junit.Assert
import org.junit.Test

/**
 * Tests for [RecursiveStopwatch]
 */
class RecursiveStopwatchTest {
  var currentTime = 0L
  var stopwatch = RecursiveStopwatch({ currentTime })

  /**
   * Verifies that elapsed time is computed correctly.
   */
  @Test
  fun testElapsedTime() {
    currentTime = 1000L
    stopwatch.start()
    currentTime = 2000L
    val elapsedTime = stopwatch.end()
    Assert.assertEquals(1000L, elapsedTime)
  }

  /**
   * Verifies that if two nested stopwatches are started, the time is correctly allocated between the inner and outer one.
   */
  @Test
  fun testNestedTime() {
    currentTime = 1000L
    stopwatch.start()
    currentTime = 1500L
    val saved = stopwatch.start()
    currentTime = 1600L
    val innerElapsed = stopwatch.end(saved)
    currentTime = 2000L
    val outerElapsed = stopwatch.end()
    Assert.assertEquals(100L, innerElapsed)
    Assert.assertEquals(900L, outerElapsed)
  }

  /**
   * Verifies that the stopwatch will return -1 if it is ended twice.
   */
  @Test
  fun testDoubleEnd() {
    currentTime = 1000L
    stopwatch.start()
    stopwatch.end()
    val endResult = stopwatch.end()
    Assert.assertEquals(-1L, endResult)
  }

  /**
   * Verifies that the stopwatch is ended without ever being started.
   */
  @Test
  fun testEndWithoutStart() {
    val endResult = stopwatch.end()
    Assert.assertEquals(-1L, endResult)
  }

  /**
   * Calling pause should cause the stopwatch to stop accumulating time, without discarding
   * the time accumulated so far.
   */
  @Test
  fun testPause() {
    currentTime = 1L
    stopwatch.start()
    currentTime = 2L
    stopwatch.pause()
    currentTime = 3L
    val result = stopwatch.end()
    Assert.assertEquals(1L, result)
  }

  /**
   * Calling pause twice should cause the second call to be ignored.
   */
  @Test
  fun testDoublePauseHasNoExtraEffect() {
    currentTime = 1L
    stopwatch.start()
    currentTime = 2L
    stopwatch.pause()
    currentTime = 3L
    stopwatch.pause()
    currentTime = 4L
    val result = stopwatch.end()
    Assert.assertEquals(1L, result)
  }

  /**
   * Calling resume should start accumulating time again.
   */
  @Test
  fun testResume() {
    currentTime = 1L
    stopwatch.start()
    currentTime = 2L
    stopwatch.pause()
    currentTime = 4L
    stopwatch.resume()
    currentTime = 7L
    val result = stopwatch.end()
    Assert.assertEquals(4L, result)
  }

  /**
   * Starting and stopping an inner timer should cause the outer one to resume.
   */
  @Test
  fun testStartingAndEndingWillResume() {
    stopwatch.start()
    stopwatch.pause()
    currentTime = 1L
    val saved = stopwatch.start()
    currentTime = 10L
    stopwatch.end(saved)
    currentTime = 20L
    val result = stopwatch.end()
    Assert.assertEquals(10L, result)
  }

  /**
   * Pausing while the stopwatch isn't running is permitted but has no effect.
   */
  @Test
  fun testPauseWhenNotRunning() {
    stopwatch.pause()
    Assert.assertEquals(-1L, stopwatch.end())
  }

  /**
   * Resuming while the stopwatch isn't running is permitted but has no effect.
   */
  @Test
  fun testResumeWhenNotRunning() {
    stopwatch.resume()
    Assert.assertEquals(-1L, stopwatch.end())
  }

  /**
   * Resuming while the stopwatch isn't paused is permitted but has no effect.
   */
  @Test
  fun testResumeWhenNotPaused() {
    stopwatch.start()
    currentTime = 1L
    stopwatch.resume()
    currentTime = 2L
    Assert.assertEquals(2L, stopwatch.end())
  }
}