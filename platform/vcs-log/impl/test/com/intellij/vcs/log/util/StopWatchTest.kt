// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StopWatchTestCase {
  @Test
  fun testEvenMinutes() {
    assertEquals("1m", StopWatch.formatTime(60000))
  }

  @Test
  fun testEvenSeconds() {
    assertEquals("1m 5s", StopWatch.formatTime(65000))
  }

  @Test
  fun testUnEvenSeconds() {
    assertEquals("1m 5.100s", StopWatch.formatTime(65100))
  }
}