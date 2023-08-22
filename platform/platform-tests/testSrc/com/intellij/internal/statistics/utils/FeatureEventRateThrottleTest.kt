// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.utils

import com.intellij.internal.statistic.utils.EventRateThrottleResult
import com.intellij.internal.statistic.utils.EventsIdentityWindowThrottle
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

  @Test
  fun `test events are reported if time was moved backwards`() {
    val throttle = EventsRateWindowThrottle(3, 10, 100)
    assertAccept(throttle.tryPass(100))
    assertAccept(throttle.tryPass(105))

    assertAccept(throttle.tryPass(90))
    assertAccept(throttle.tryPass(97))
    assertAccept(throttle.tryPass(98))

    assertAccept(throttle.tryPass(89))
    assertAccept(throttle.tryPass(88))
    assertAccept(throttle.tryPass(87))
    assertDenyAndReport(throttle.tryPass(86))
  }

  @Test
  fun `test consecutive events by different keys in one period`() {
    val throttle = EventsIdentityWindowThrottle(3, 3, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("foo", 103))
    assertAccept(throttle.tryPass("foo", 105))

    assertAccept(throttle.tryPass("bar", 105))
    assertAccept(throttle.tryPass("bar", 107))
    assertAccept(throttle.tryPass("bar", 109))
  }

  @Test
  fun `test events by different keys merged in one period`() {
    val throttle = EventsIdentityWindowThrottle(3, 3, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("bar", 101))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 105))
    assertAccept(throttle.tryPass("bar", 108))
    assertAccept(throttle.tryPass("bar", 108))
  }

  @Test
  fun `test too many events by different keys in one period`() {
    val throttle = EventsIdentityWindowThrottle(3, 3, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("bar", 101))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 105))
    assertAccept(throttle.tryPass("bar", 108))

    assertDenyAndReport(throttle.tryPass("foo", 108))

    assertAccept(throttle.tryPass("bar", 108))

    assertDenyAndReport(throttle.tryPass("bar", 109))

    assertDeny(throttle.tryPass("foo", 109))
    assertDeny(throttle.tryPass("bar", 109))
  }

  @Test
  fun `test too many events by one key`() {
    val throttle = EventsIdentityWindowThrottle(5, 5, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("bar", 101))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 105))
    assertAccept(throttle.tryPass("foo", 106))
    assertAccept(throttle.tryPass("bar", 108))
    assertAccept(throttle.tryPass("foo", 107))

    assertDenyAndReport(throttle.tryPass("foo", 108))
    assertDeny(throttle.tryPass("foo", 108))
    assertDeny(throttle.tryPass("foo", 108))
    assertDeny(throttle.tryPass("foo", 109))

    assertAccept(throttle.tryPass("bar", 109))
  }

  @Test
  fun `test counter by different keys reset in new period`() {
    val throttle = EventsIdentityWindowThrottle(3, 3, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("bar", 101))
    assertAccept(throttle.tryPass("bar", 102))
    assertAccept(throttle.tryPass("bar", 103))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 104))

    assertDenyAndReport(throttle.tryPass("foo", 105))
    assertDenyAndReport(throttle.tryPass("bar", 107))

    assertDeny(throttle.tryPass("foo", 108))
    assertDeny(throttle.tryPass("bar", 108))

    assertAccept(throttle.tryPass("foo", 111))
    assertAccept(throttle.tryPass("foo", 113))
    assertAccept(throttle.tryPass("bar", 115))
  }

  @Test
  fun `test counter by different keys dont reach limit`() {
    val throttle = EventsIdentityWindowThrottle(3, 3, 10)
    assertAccept(throttle.tryPass("foo", 100))
    assertAccept(throttle.tryPass("foo", 103))
    assertAccept(throttle.tryPass("bar", 105))
    assertAccept(throttle.tryPass("bar", 108))
    assertAccept(throttle.tryPass("bar", 109))

    assertAccept(throttle.tryPass("foo", 113))
    assertAccept(throttle.tryPass("bar", 115))
    assertAccept(throttle.tryPass("foo", 115))
    assertAccept(throttle.tryPass("foo", 117))

    assertDenyAndReport(throttle.tryPass("foo", 117))

    assertAccept(throttle.tryPass("bar", 117))
    assertAccept(throttle.tryPass("bar", 118))

    assertDenyAndReport(throttle.tryPass("bar", 119))

    assertDeny(throttle.tryPass("foo", 119))
  }

  @Test
  fun `test alert threshold is reached`() {
    val throttle = EventsIdentityWindowThrottle(5, 3, 10)
    assertAccept(throttle.tryPass("foo", 103))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 104))

    assertAlert(throttle.tryPass("foo", 106))
    assertAccept(throttle.tryPass("foo", 108))

    assertDenyAndReport(throttle.tryPass("foo", 108))
    assertDeny(throttle.tryPass("foo", 109))
  }

  @Test
  fun `test alert by different keys threshold is reached`() {
    val throttle = EventsIdentityWindowThrottle(5, 3, 10)
    assertAccept(throttle.tryPass("bar", 100))
    assertAccept(throttle.tryPass("bar", 101))
    assertAccept(throttle.tryPass("foo", 101))
    assertAccept(throttle.tryPass("bar", 103))
    assertAccept(throttle.tryPass("foo", 104))
    assertAccept(throttle.tryPass("foo", 104))

    assertAlert(throttle.tryPass("foo", 106))
    assertAlert(throttle.tryPass("bar", 107))
    assertAccept(throttle.tryPass("bar", 107))
    assertAccept(throttle.tryPass("foo", 108))

    assertDenyAndReport(throttle.tryPass("foo", 108))
    assertDenyAndReport(throttle.tryPass("bar", 109))
    assertDeny(throttle.tryPass("foo", 109))
    assertDeny(throttle.tryPass("bar", 109))
  }

  @Test
  fun `test alert disabled`() {
    val throttle = EventsIdentityWindowThrottle(3, -1, 10)
    assertAccept(throttle.tryPass("bar", 100))
    assertAccept(throttle.tryPass("bar", 102))
    assertAccept(throttle.tryPass("bar", 105))
    assertDenyAndReport(throttle.tryPass("bar", 106))
    assertDeny(throttle.tryPass("bar", 109))
  }

  private fun assertAccept(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.ACCEPT, result)
  }

  private fun assertAlert(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.ALERT, result)
  }

  private fun assertDenyAndReport(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.DENY_AND_REPORT, result)
  }

  private fun assertDeny(result: EventRateThrottleResult) {
    assertEquals(EventRateThrottleResult.DENY, result)
  }
}