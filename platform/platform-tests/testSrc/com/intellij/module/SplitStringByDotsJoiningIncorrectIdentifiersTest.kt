// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.module

import com.intellij.openapi.module.impl.splitStringByDotsJoiningIncorrectIdentifiers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @author nik
 */
class SplitStringByDotsJoiningIncorrectIdentifiersTest {
  @Test
  fun simple() {
    splitAndCheck("a", "a")
    splitAndCheck("a.b", "a", "b")
    splitAndCheck("foo", "foo")
    splitAndCheck("foo.bar", "foo", "bar")
  }

  @Test
  fun `incorrect chars`() {
    assertDoNotSplit("a.1")
    assertDoNotSplit("a-1.2")
    assertDoNotSplit("a. b.")
    splitAndCheck("foo.bar-1.2.3", "foo", "bar-1.2.3")
    splitAndCheck("a..b", "a.", "b")
  }

  @Test
  fun `corner cases`() {
    assertDoNotSplit("")
    assertDoNotSplit(".")
    assertDoNotSplit(" ")
    assertDoNotSplit("..")
    assertDoNotSplit("...")
    assertDoNotSplit(".foo")
    splitAndCheck("..foo", ".", "foo")
    assertDoNotSplit("foo.")
    assertDoNotSplit(".foo.")
    assertDoNotSplit("foo..")
  }

  private fun assertDoNotSplit(s: String) {
    splitAndCheck(s, s)
  }

  private fun splitAndCheck(s: String, vararg expected: String) {
    val (list, last) = splitStringByDotsJoiningIncorrectIdentifiers(s)
    if (list.isNotEmpty()) {
      assertEquals(list.last(), last)
    }
    else {
      assertEquals("", last)
    }
    assertEquals(expected.toList(), list)
  }
}