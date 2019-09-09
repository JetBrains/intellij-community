// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsIdentityThrottleTest {

  @Test
  fun `test tryPass`() {
    val throttle = EventsIdentityThrottle(3, 10)

    assertTrue(throttle.tryPass(1, 0))
    assertTrue(throttle.tryPass(2, 2))
    assertFalse(throttle.tryPass(1, 4))
    assertTrue(throttle.tryPass(3, 5))

    assertFalse(throttle.tryPass(1, 6))
    assertFalse(throttle.tryPass(2, 7))
    assertFalse(throttle.tryPass(3, 8))
    assertFalse(throttle.tryPass(1, 9))

    assertTrue(throttle.tryPass(1, 10))
    assertTrue(throttle.tryPass(4, 11))
    assertTrue(throttle.tryPass(5, 12))
    assertTrue(throttle.tryPass(6, 13))

    assertFalse(throttle.tryPass(4, 14))
    assertFalse(throttle.tryPass(5, 15))
    assertFalse(throttle.tryPass(6, 16))
    assertFalse(throttle.tryPass(5, 21))

    assertTrue(throttle.tryPass(4, 21))
    assertTrue(throttle.tryPass(5, 22))
    assertTrue(throttle.tryPass(6, 23))

    assertTrue(throttle.tryPass(1, 24))
    assertTrue(throttle.tryPass(2, 25))
    assertTrue(throttle.tryPass(3, 26))
  }
}