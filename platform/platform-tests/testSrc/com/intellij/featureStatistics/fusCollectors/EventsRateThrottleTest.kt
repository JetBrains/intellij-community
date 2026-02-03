// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.featureStatistics.fusCollectors

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventsRateThrottleTest {

  @Test
  fun `test tryPass`() {
    val throttle = EventsRateThrottle(3, 10)

    assertTrue(throttle.tryPass(0))
    assertTrue(throttle.tryPass(2))
    assertTrue(throttle.tryPass(4))

    assertFalse(throttle.tryPass(6))
    assertFalse(throttle.tryPass(8))

    assertTrue(throttle.tryPass(10))

    assertFalse(throttle.tryPass(11))

    assertTrue(throttle.tryPass(12))

    assertTrue(throttle.tryPass(20))
  }
}