// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsIdentityThrottleTest {

  private val ONE = -67834
  private val TWO = 908542
  private val THREE = 12314
  private val FOUR = 482309581

  @Test
  fun `test throttling`() {
    val timer = Timer()
    val throttle = EventsIdentityThrottle(2, 10)

    assertTrue(throttle.tryPass(ONE, timer.tick()))
    assertTrue(throttle.tryPass(TWO, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == ONE)

    assertFalse(throttle.tryPass(ONE, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == ONE)

    assertFalse(throttle.tryPass(TWO, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == ONE)
  }

  @Test
  fun `test max size`() {
    val timer = Timer()
    val throttle = EventsIdentityThrottle(2, 10)

    assertTrue(throttle.tryPass(ONE, timer.tick()))
    assertTrue(throttle.tryPass(TWO, timer.tick()))

    assertTrue(throttle.tryPass(THREE, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == TWO)

    assertTrue(throttle.tryPass(FOUR, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == THREE)
  }

  @Test
  fun `test timeout`() {
    val timer = Timer()
    val throttle = EventsIdentityThrottle(2, 10)

    assertTrue(throttle.tryPass(ONE, timer.tick()))
    assertTrue(throttle.tryPass(TWO, timer.tick()))

    assert(throttle.size(timer.bigTick(9)) == 1)
    assert(throttle.getOldest(timer.now()) == TWO)

    assertTrue(throttle.tryPass(ONE, timer.tick()))

    assert(throttle.size(timer.now()) == 1)
    assert(throttle.getOldest(timer.now()) == ONE)

    assertTrue(throttle.tryPass(TWO, timer.tick()))

    assert(throttle.size(timer.now()) == 2)
    assert(throttle.getOldest(timer.now()) == ONE)
  }

  private class Timer {

    private var time: Long = -572893L

    fun now(): Long {
      return time
    }

    fun tick(): Long {
      time += 1
      return time
    }

    fun bigTick(value: Long): Long {
      time += value
      return time
    }
  }
}