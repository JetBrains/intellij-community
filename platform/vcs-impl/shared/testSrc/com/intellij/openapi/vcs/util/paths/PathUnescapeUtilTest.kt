// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.util.paths

import com.intellij.openapi.vcs.util.paths.PathUnescapeUtil.unescape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PathUnescapeUtilTest {
  @Test
  fun `a path without escapes stays the same`() {
    assertEquals("dir/file.txt", unescape("dir/file.txt"))
  }

  @Test
  fun `a path with spaces stays the same`() {
    assertEquals("my dir/my file.txt", unescape("my dir/my file.txt"))
  }

  @Test
  fun `the enclosing quotes are removed`() {
    assertEquals("dir/file.txt", unescape("\"dir/file.txt\""))
  }

  @Test
  fun `a quote on one side only stays`() {
    assertEquals("\"dir/file.txt", unescape("\"dir/file.txt"))
    assertEquals("dir/file.txt\"", unescape("dir/file.txt\""))
  }

  @Test
  fun `an escaped quote is unescaped`() {
    assertEquals("\"quote", unescape("\"\\\"quote\""))
  }

  @Test
  fun `the single character escapes are unescaped`() {
    assertEquals("\\", unescape("\\\\"))
    assertEquals("\t", unescape("\\t"))
    assertEquals("\n", unescape("\\n"))
    assertEquals("\r", unescape("\\r"))
    assertEquals("\u0007", unescape("\\a"))
    assertEquals("\b", unescape("\\b"))
    assertEquals("\u000c", unescape("\\f"))
    assertEquals("\"", unescape("\\\""))
  }

  @Test
  fun `an octal escape of an ASCII character is unescaped`() {
    assertEquals("a1b", unescape("a\\061b"))
  }

  @Test
  fun `an octal escape with less than three digits is unescaped`() {
    assertEquals("\u0001x", unescape("\\1x"))
    assertEquals("\u0007x", unescape("\\07x"))
  }

  @Test
  fun `an octal escape takes at most three digits`() {
    assertEquals("17", unescape("\\0617"))
  }

  @Test
  fun `the octal escapes of a multibyte character are decoded together`() {
    assertEquals("папка/file.txt", unescape("\\320\\277\\320\\260\\320\\277\\320\\272\\320\\260/file.txt"))
    assertEquals("é", unescape("\\303\\251"))
    assertEquals("😀", unescape("\\360\\237\\230\\200"))
  }

  @Test
  fun `a quoted path with octal escapes is unescaped`() {
    assertEquals("dir/é.txt", unescape("\"dir/\\303\\251.txt\""))
  }

  @Test
  fun `an octal escape followed by a character escape is unescaped`() {
    assertEquals("é\n", unescape("\\303\\251\\n"))
    assertEquals("é\\", unescape("\\303\\251\\\\"))
  }

  @Test
  fun `an unterminated escape throws an exception`() {
    assertThrows<IllegalArgumentException> { unescape("dir\\") }
  }

  @Test
  fun `an unknown escape throws an exception`() {
    assertThrows<IllegalArgumentException> { unescape("dir\\x") }
  }
}
