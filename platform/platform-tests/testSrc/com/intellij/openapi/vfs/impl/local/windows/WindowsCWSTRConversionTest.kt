// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local.windows

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.foreign.Arena

/**
 * Verifies the raw little-endian UTF-16 code-unit conversion between Java [String]s and Windows `WCHAR` wide strings.
 * It must preserve unpaired surrogates, which Windows file names (WTF-16) may contain and a `UTF_16LE` charset rejects.
 * The conversion is pure memory copying (no native calls), so this test is platform-independent.
 */
class WindowsCWSTRConversionTest {
  private fun roundTrip(s: String): String =
    Arena.ofConfined().use { arena ->
      toJavaStringFromWinCWSTR(toWinReadonlyCWSTR(arena, s), s.length)
    }

  @Test
  fun loneHighSurrogateIsPreserved() {
    val s = "x\uD83Dy"
    assertEquals(s, roundTrip(s))
  }

  @Test
  fun loneLowSurrogateIsPreserved() {
    val s = "\uDE00z"
    assertEquals(s, roundTrip(s))
  }

  @Test
  fun encodingIsLittleEndianAndNullTerminated() {
    Arena.ofConfined().use { arena ->
      val bytes = toWinReadonlyCWSTR(arena, "A").asByteBuffer() // U+0041
      assertEquals(0x41.toByte(), bytes.get(0)) // low byte first (little-endian)
      assertEquals(0x00.toByte(), bytes.get(1)) // high byte
      assertEquals(0x00.toByte(), bytes.get(2)) // null terminator
      assertEquals(0x00.toByte(), bytes.get(3))
    }
  }
}
