// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.AnsiCsiUtil.containsAnsi
import com.intellij.util.AnsiCsiUtil.stripAnsi
import junit.framework.TestCase

internal class AnsiCsiTest : TestCase() {

  fun `test simple ANSI CSI strip`() {
    val actual = stripAnsi("_SOME_TEXT_\u001B[;123;456;789;m_SOME_TEXT_\u001B[;123;456;789;m_SOME_TEXT_")
    assertEquals("_SOME_TEXT__SOME_TEXT__SOME_TEXT_", actual)
  }

  fun `test ANSI CSI stripping should ignore incomplete pattern`() {
    // the 1st CSI is not complete here and should not be stripped
    val actual = stripAnsi("_SOME_TEXT_\u001B[1_SOME_TEXT_\u001B[;123;456;789;m_SOME_TEXT_")
    assertEquals("_SOME_TEXT_\u001B[1_SOME_TEXT__SOME_TEXT_", actual)
  }

  fun `test contains ANSI CSI pattern`() {
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[1m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[22222m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[;m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[;;;;;m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[1;20;300m_SOME_TEXT_"))
    assertTrue(containsAnsi("_SOME_TEXT_\u001B[1;20;300;m_SOME_TEXT_"))
  }

  fun `test does not contain ANSI CSI pattern`() {
    assertFalse(containsAnsi("_SOME_TEXT_[1111m_SOME_TEXT_"))
    assertFalse(containsAnsi("_SOME_TEXT_\u001B1111m_SOME_TEXT_"))
    assertFalse(containsAnsi("_SOME_TEXT_\u001B[1111_SOME_TEXT_"))
    assertFalse(containsAnsi("_SOME_TEXT_\u001B_SOME_TEXT_"))
  }
}