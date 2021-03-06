// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.eventLog.EventLogBuild
import com.intellij.testFramework.UsefulTestCase

class EventLogBuildNumberTest : UsefulTestCase() {

  private fun doTest(version: String, expected: EventLogBuild?) {
    val actual = EventLogBuild.fromString(version)
    assertEquals(expected, actual)
  }

  fun test_parse_build_number() {
    doTest("193.15.8", EventLogBuild(193, 15, 8))
  }

  fun test_parse_build_number_with_product() {
    doTest("UI-193.15.8", EventLogBuild(193, 15, 8))
  }

  fun test_parse_build_number_with_zero() {
    doTest("193.15.0.8", EventLogBuild(193, 15, 0, 8))
  }

  fun test_parse_build_number_with_empty_parts() {
    doTest("193.15..8", EventLogBuild(193, 15, 8))
  }

  fun test_parse_build_number_with_single_number() {
    doTest("193", EventLogBuild(193, 0))
  }

  fun test_parse_build_number_with_ending_dot() {
    doTest("193.", EventLogBuild(193, 0))
  }

  fun test_parse_build_number_with_text() {
    doTest("193.SNAPSHOT", EventLogBuild(193, 0))
  }

  fun test_parse_text_build_number() {
    doTest("SNAPSHOT", EventLogBuild(0, 0))
  }

  fun test_parse_long_build_number() {
    doTest(
      "193.15.8.1.312.457.123.87.23.2",
      EventLogBuild(193, 15, 8, 1, 312, 457, 123, 87, 23, 2)
    )
  }

  fun test_parse_invalid_build_number_with_spaces() {
    doTest("  ", null)
  }

  fun test_parse_invalid_build_number_with_suffix() {
    doTest("193.1.3-UI", EventLogBuild(0, 0))
  }
}
