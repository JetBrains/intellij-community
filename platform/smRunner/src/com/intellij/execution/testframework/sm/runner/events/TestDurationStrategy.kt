// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner.events

fun getDurationStrategy(durationStrategy: String?) = durationStrategy?.let { strategy ->
  TestDurationStrategy.values().firstOrNull { it.name.equals(strategy, ignoreCase = true) }
} ?: TestDurationStrategy.AUTOMATIC

/**
 * How [com.intellij.execution.testframework.sm.runner.SMTestProxy] calculated duration
 */
enum class TestDurationStrategy {
  /**
   * Duration must be set explicitly for tests (leaves), but for suites (branches) duration is sum of all children
   */
  AUTOMATIC,

  /**
   * No duration is calculated automatically. One must set it explicitly both for tests (leaves) and suites (branches)
   */
  MANUAL
}