// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.uploader.EventLogUploaderCliParser
import com.intellij.testFramework.UsefulTestCase
import junit.framework.TestCase

class EventLogUploaderArgumentParserTest : UsefulTestCase() {

  private fun doTest(arguments: Array<String>, vararg expected: Pair<String, String?>) {
    val actual = EventLogUploaderCliParser.parseOptions(arguments)
    TestCase.assertEquals(hashMapOf(*expected), actual)
  }

  fun test_parsing_all_arguments() {
    doTest(
      arrayOf("--bucket", "123", "--recorder", "FUS", "--test", "--device", "12345", "--url", "http://some.url", "--internal"),
      "--bucket" to "123",
      "--recorder" to "FUS",
      "--test" to null,
      "--device" to "12345",
      "--url" to "http://some.url",
      "--internal" to null
    )
  }

  fun test_next_option_instead_of_value() {
    doTest(
      arrayOf("--bucket", "123", "--recorder", "FUS", "--test", "--device", "12345", "--url", "--internal"),
      "--bucket" to "123",
      "--recorder" to "FUS",
      "--test" to null,
      "--device" to "12345",
      "--url" to null,
      "--internal" to null
    )
  }

  fun test_end_instead_of_value() {
    doTest(
      arrayOf("--bucket", "123", "--recorder", "FUS", "--test", "--device", "12345", "--url"),
      "--bucket" to "123",
      "--recorder" to "FUS",
      "--test" to null,
      "--device" to "12345",
      "--url" to null
    )
  }

  fun test_two_values() {
    doTest(
      arrayOf("--bucket", "123", "--recorder", "FUS", "--test", "--device", "12345", "6789"),
      "--bucket" to "123",
      "--recorder" to "FUS",
      "--test" to null,
      "--device" to "12345"
    )
  }

  fun test_first_value() {
    doTest(
      arrayOf("12345", "--bucket", "123", "--recorder", "FUS", "--test", "--device", "12345"),
      "--bucket" to "123",
      "--recorder" to "FUS",
      "--test" to null,
      "--device" to "12345"
    )
  }
}