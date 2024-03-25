// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.internal

import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.tooling.util.JavaVersionMatcher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaVersionMatcherTest {

  @Test
  fun `test java greater than`() {
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "8"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "8+"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "8               +"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "+                8"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "1.8"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "== 8"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "<                  11"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "<11"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "<= 11"))
    assertTrue(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("11"), "1.8+"))

    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "11"))
    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "> 8"))
    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "!8"))
    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), "hello"))
    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), ""))
    assertFalse(JavaVersionMatcher.isVersionMatch(JavaVersion.parse("1.8"), ">8="))
  }
}