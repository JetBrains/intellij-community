// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log

import org.junit.Assert.assertEquals
import org.junit.Test

class VcsUserParserTest {
  @Test
  fun add_space_before_email_in_brackets() {
    assertCorrection("foo<foo@email.com>", "foo <foo@email.com>")
  }

  @Test
  fun add_brackets() {
    val expected = "John Smith <john.smith@email.com>"
    assertCorrection("John Smith john.smith@email.com", expected)
    assertCorrection("John Smith <john.smith@email.com", expected)
    assertCorrection("John Smith john.smith@email.com>", expected)
  }

  @Test
  fun no_correction_needed() {
    assertDoNothing("John Smith <john.smith@email.com>")
  }

  @Test
  fun correction_not_possible() {
    assertDoNothing("foo")
    assertDoNothing("foo bar")
  }

  private fun assertCorrection(source: String, expected: String) = assertEquals(expected, VcsUserParser.correct(source))
  private fun assertDoNothing(source: String) = assertCorrection(source, source)
}