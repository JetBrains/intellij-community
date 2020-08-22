// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.utils

import com.intellij.internal.statistic.utils.EventRateThrottleResult
import com.intellij.internal.statistic.utils.EventsRateWindowThrottle
import org.junit.Test
import kotlin.test.assertEquals

class FeatureEventRateThrottleTest {

  @Test
  fun `test too many events in one period`() {
    val throttle = EventsRateWindowThrottle(5, 10, 100)
    assertAccept(throttle.tryPass(100))
    assertAccept(throttle.tryPass(101))
    assertAccept(throttle.tryPass(103))
    assertAccept(throttle.tryPass(104))
    assertAccept(throttle.tryPass(107))
    assertDenyAndReport(throttle.tryPass(108))
    assertDeny(throttle.tryPass(109))
  }

  @Test
  fun `test counter reset in new period`() {
    val throttle = EventsRateWindowThrottle(3, 10, 100)
    assertAccept(throttle.tryPass(100))
    assertAccept(throttle.tryPass(101))
    assertAccept(throttle.tryPass(103))
    assertDenyAndReport(throttle.tryPass(104))
    assertDeny(throttle.tryPass(107))
    assertDeny(throttle.tryPass(108))
    assertDeny(throttle.tryPass(109))
    assertAccept(throttle.tryPass(110))
    assertAccept(throttle.tryPass(110))
    assertAccept(throttle.tryPass(118))
    assertDenyAndReport(throttle.tryPass(119))
    assertDeny(throttle.tryPass(119))
  }

  @Test
  fun `test counter dont reach limit`() {
    val throttle = EventsRateWindowThrottle(3, 10, 100)
    assertAccept(throttle.tryPass(100))
    assertAccept(throttle.tryPass(105))

    assertAccept(throttle.tryPass(110))
    assertAccept(throttle.tryPass(113))
    assertAccept(throttle.tryPass(118))

    assertAccept(throttle.tryPass(128))

    assertAccept(throttle.tryPass(136))

    assertAccept(throttle.tryPass(144))
    assertAccept(throttle.tryPass(145))
    assertAccept(throttle.tryPass(146))
    assertDenyAndReport(throttle.tryPass(147))
  }


  @Test
  fun `test period skipped dont reach limit`() {
    val throttle = EventsRateWindowThrottle(3, 10, 100)
    assertAccept(throttle.tryPass(100))
    assertAccept(throttle.tryPass(105))

    assertAccept(throttle.tryPass(120))
    assertAccept(throttle.tryPass(123))
    assertAccept(throttle.tryPass(128))

    assertAccept(throttle.tryPass(144))
    assertAccept(throttle.tryPass(145))
    assertAccept(throttle.tryPass(146))
    assertDenyAndReport(throttle.tryPass(147))
  }

  private fun assertAccept(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.ACCEPT, result)
  }

  private fun assertDenyAndReport(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.DENY_AND_REPORT, result)
  }

  private fun assertDeny(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.DENY, result)
  }
}