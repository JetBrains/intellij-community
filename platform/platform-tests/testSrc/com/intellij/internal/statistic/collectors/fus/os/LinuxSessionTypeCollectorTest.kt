// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import kotlin.test.assertEquals

@RunWith(Parameterized::class)
class LinuxSessionTypeCollectorTest(private val sessionTypeEnvVar: String?, private val reportedValue: String) {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data() : List<Array<String?>> {
      return listOf(
          arrayOf(null, "empty"),
          arrayOf("foo", "Unknown"),
          arrayOf("tty", "Terminal"),
          arrayOf("x11", "X11"),
          arrayOf("wayland", "Wayland"))
    }
  }

  @Test
  fun `test session type parser`() {
    assertEquals(reportedValue, LinuxWindowManagerUsageCollector.toReportedSessionName(sessionTypeEnvVar))
  }
}