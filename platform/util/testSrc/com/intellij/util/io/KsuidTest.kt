// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.io

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KsuidTest {
  @Test
  fun `valid 27-char Base62 string is accepted`() {
    assertTrue(Ksuid.isValid("0ujtsYcgvSTl8PAuAdqWYSMnLOv"))
  }

  @Test
  fun `valid short Base62 string is accepted`() {
    assertTrue(Ksuid.isValid("abc123XYZ"))
  }

  @Test
  fun `a generated value is accepted`() {
    assertTrue(Ksuid.isValid(Ksuid.generate()))
  }

  @Test
  fun `null is rejected`() {
    assertFalse(Ksuid.isValid(null))
  }

  @Test
  fun `empty string is rejected`() {
    assertFalse(Ksuid.isValid(""))
  }

  @Test
  fun `string longer than MAX_ENCODED_LENGTH is rejected`() {
    // 28 Base62 chars, one over MAX_ENCODED_LENGTH (27)
    assertFalse(Ksuid.isValid("abcdefghijklmnopqrstuvwxyzAB"))
  }

  @Test
  fun `non-Base62 characters are rejected`() {
    assertFalse(Ksuid.isValid("a/b"))
    assertFalse(Ksuid.isValid("a\\b"))
    assertFalse(Ksuid.isValid("a.b"))
    assertFalse(Ksuid.isValid("a-b"))
    assertFalse(Ksuid.isValid("a b"))
    assertFalse(Ksuid.isValid("../../etc/passwd"))
  }
}