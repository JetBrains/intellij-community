// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GradleJvmSupportMatricesTest : GradleJvmSupportMatricesTestCase() {

  @Test
  fun `test compatibility between gradle and java versions`() {
    assertTrue(isSupported("2.0", 6))
    assertTrue(isSupported("4.0", 6))
    assertFalse(isSupported("5.0", 6))

    assertTrue(isSupported("2.0", 7))
    assertTrue(isSupported("4.0", 7))
    assertTrue(isSupported("4.9", 7))
    assertFalse(isSupported("5.0", 7))

    assertTrue(isSupported("0.9.2", 8))
    assertTrue(isSupported("3.0", 8))
    assertTrue(isSupported("4.0", 8))
    assertFalse(isSupported("5.1", 8))
    assertFalse(isSupported("5.4.1", 8))
    assertFalse(isSupported("6.0", 8))
    assertFalse(isSupported("7.1", 8))
    assertTrue(isSupported("7.2", 8))
    assertTrue(isSupported("7.5", 8))

    assertFalse(isSupported("4.8", 11))
    assertTrue(isSupported("5.0", 11))
    assertTrue(isSupported("5.4.1", 11))
    assertTrue(isSupported("7.5", 11))

    assertFalse(isSupported("7.1", 17))
    assertTrue(isSupported("7.2", 17))
    assertTrue(isSupported("7.3", 17))
    assertTrue(isSupported("7.5", 17))

    assertFalse(isSupported("7.4", 18))
    assertTrue(isSupported("7.5", 18))
    assertTrue(isSupported("7.5.1", 18))

    assertFalse(isSupported("7.5", 19))
    assertFalse(isSupported("7.5.1", 19))
    assertTrue(isSupported("7.6", 19))
  }

  @Test
  fun `test suggesting gradle version for java version`() {
    val bundledGradleVersion = GradleVersion.current()
    require(bundledGradleVersion >= GradleVersion.version("7.1"))

    assertEquals("4.10.3", suggestGradleVersion(6))
    assertEquals("4.10.3", suggestGradleVersion(7))
    assertEquals(bundledGradleVersion.version, suggestGradleVersion(8))
    assertEquals(bundledGradleVersion.version, suggestGradleVersion(11))
    assertEquals(bundledGradleVersion.version, suggestGradleVersion(15))
    assertEquals(bundledGradleVersion.version, suggestGradleVersion(17))
    assertEquals(bundledGradleVersion.version, suggestGradleVersion(18))
    //assertEquals(bundledGradleVersion.version, suggestGradleVersion(19))
  }

  @Test
  fun `test suggesting java version for gradle version`() {
    assertEquals(8, suggestJavaVersion("0.9.2"))
    assertEquals(8, suggestJavaVersion("2.0"))
    assertEquals(8, suggestJavaVersion("3.0"))
    assertEquals(9, suggestJavaVersion("4.3"))
    assertEquals(10, suggestJavaVersion("4.7"))
    assertEquals(11, suggestJavaVersion("5.0"))
    assertEquals(11, suggestJavaVersion("5.1"))
    assertEquals(12, suggestJavaVersion("5.4"))
    assertEquals(13, suggestJavaVersion("6.0"))
    assertEquals(14, suggestJavaVersion("6.3"))
    assertEquals(15, suggestJavaVersion("6.7"))
    assertEquals(16, suggestJavaVersion("7.1"))
    assertEquals(17, suggestJavaVersion("7.2"))
    assertEquals(17, suggestJavaVersion("7.4"))
    assertEquals(18, suggestJavaVersion("7.5"))
    assertEquals(18, suggestJavaVersion("7.5.1"))
    assertEquals(19, suggestJavaVersion("7.6"))
  }

  @Test
  fun `test suggesting oldest compatible gradle version for java version`() {
    assertEquals("3.0", suggestOldestCompatibleGradleVersion(6))
    assertEquals("3.0", suggestOldestCompatibleGradleVersion(7))
    assertEquals("3.0", suggestOldestCompatibleGradleVersion(8))
    assertEquals("4.3", suggestOldestCompatibleGradleVersion(9))
    assertEquals("4.7", suggestOldestCompatibleGradleVersion(10))
    assertEquals("5.0", suggestOldestCompatibleGradleVersion(11))
    assertEquals("5.4", suggestOldestCompatibleGradleVersion(12))
    assertEquals("6.0", suggestOldestCompatibleGradleVersion(13))
    assertEquals("6.3", suggestOldestCompatibleGradleVersion(14))
    assertEquals("6.7", suggestOldestCompatibleGradleVersion(15))
    assertEquals("7.0", suggestOldestCompatibleGradleVersion(16))
    assertEquals("7.2", suggestOldestCompatibleGradleVersion(17))
    assertEquals("7.5", suggestOldestCompatibleGradleVersion(18))
    //assertEquals("7.6", suggestOldestCompatibleGradleVersion(19))
    //assertEquals("7.6", suggestOldestCompatibleGradleVersion(24))
  }

  @Test
  fun `test suggesting oldest compatible java version for gradle version`() {
    assertEquals(7, suggestOldestCompatibleJavaVersion("2.0"))
    assertEquals(7, suggestOldestCompatibleJavaVersion("3.0"))
    assertEquals(8, suggestOldestCompatibleJavaVersion("5.0"))
    assertEquals(9, suggestOldestCompatibleJavaVersion("5.1"))
    assertEquals(9, suggestOldestCompatibleJavaVersion("7.1"))
    assertEquals(8, suggestOldestCompatibleJavaVersion("7.2"))
    assertEquals(8, suggestOldestCompatibleJavaVersion("7.5"))
    assertEquals(8, suggestOldestCompatibleJavaVersion("7.6"))
  }
}