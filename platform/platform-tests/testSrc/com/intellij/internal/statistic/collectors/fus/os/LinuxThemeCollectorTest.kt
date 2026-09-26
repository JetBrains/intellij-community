// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.os

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LinuxThemeCollectorTest {

  private val themes: Map<String?, String> = mapOf(
    null to LinuxWindowManagerUsageCollector.EMPTY_THEME,
    "blablabla" to LinuxWindowManagerUsageCollector.UNKNOWN_THEME,
    "Breeze" to "Breeze",
    "breeze" to "Breeze",
    "Breeze-dark" to "Breeze-dark",
    "Yaru-some_family" to "Yaru-*",
  )

  @Test
  fun testToReportedTheme() {
    for ((value, expected) in themes) {
      assertEquals(expected, LinuxWindowManagerUsageCollector.toReportedTheme(value))
      assertTrue(LinuxWindowManagerUsageCollector.ALL_THEME_NAMES.contains(expected))
    }
  }
}