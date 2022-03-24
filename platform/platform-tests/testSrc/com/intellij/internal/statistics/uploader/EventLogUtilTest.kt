// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.uploader

import com.intellij.internal.statistic.config.StatisticsStringUtil
import com.intellij.testFramework.UsefulTestCase


class EventLogUtilTest : UsefulTestCase() {

  private fun doTestStringSplit(str: String, separator: Char, expected: List<String>) {
    val actual = StatisticsStringUtil.split(str, separator)
    assertEquals(expected, actual)
  }

  fun test_split_build_number() {
    doTestStringSplit("193.12345.124", '.', arrayListOf("193", "12345", "124"))
  }

  fun test_split_build_number_with_ending_dot() {
    doTestStringSplit("193.12345.", '.', arrayListOf("193", "12345"))
  }

  fun test_split_build_number_with_start_dot() {
    doTestStringSplit(".193.12345", '.', arrayListOf("193", "12345"))
  }

  fun test_split_build_number_without_dot() {
    doTestStringSplit("19312345", '.', arrayListOf("19312345"))
  }

  fun test_split_empty_string() {
    doTestStringSplit("", '.', arrayListOf())
  }

  fun test_split_only_separator_string() {
    doTestStringSplit(".", '.', arrayListOf())
  }

  fun test_split_files() {
    doTestStringSplit("/a/b/c:/d/e", ':', arrayListOf("/a/b/c", "/d/e"))
  }

  fun test_split_files_win() {
    doTestStringSplit("/a/b/c;/d/e", ';', arrayListOf("/a/b/c", "/d/e"))
  }
}
