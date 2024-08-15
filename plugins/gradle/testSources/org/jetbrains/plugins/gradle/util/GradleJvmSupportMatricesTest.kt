// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util

import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix

class GradleJvmSupportMatricesTest : GradleJvmSupportMatricesTestCase() {

  companion object {
    private const val FUTURE_GRADLE_VERSION = "100.0"
    private const val LATEST_SUPPORTED_JAVA_VERSION = 22
    private const val FUTURE_JAVA_VERSION = 100
    private const val BUNDLED_GRADLE_VERSION = "8.9"
    private const val LATEST_GRADLE_VERSION = "8.10"
  }

  fun `test bundled gradle version`() {
    assertEquals(BUNDLED_GRADLE_VERSION, GradleVersion.current().version)
  }

  fun `test latest gradle version`() {
    assertEquals(LATEST_GRADLE_VERSION, GradleJvmSupportMatrix.getAllSupportedGradleVersionsByIdea().last().version)
  }

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
    assertTrue(isSupported("5.1", 8))
    assertTrue(isSupported("5.4.1", 8))
    assertTrue(isSupported("6.0", 8))
    assertTrue(isSupported("7.1", 8))
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

    assertFalse(isSupported("8.2", 20))
    assertTrue(isSupported("8.3", 20))
    assertTrue(isSupported("8.4", 20))

    assertFalse(isSupported("8.4", 21))
    assertTrue(isSupported("8.5", 21))

    assertFalse(isSupported("8.7", 22))
    assertTrue(isSupported("8.8", 22))
  }

  fun `test suggesting gradle version for java version`() {
    assertEquals("4.10.3", suggestGradleVersion(6))
    assertEquals("4.10.3", suggestGradleVersion(7))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(8))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(11))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(17))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(19))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(20))
    assertEquals(BUNDLED_GRADLE_VERSION, suggestGradleVersion(21))

    assertEquals(null, suggestGradleVersion(FUTURE_JAVA_VERSION))
  }

  fun `test suggesting gradle version for latest java version`() {
    assertTrue(isSupported(BUNDLED_GRADLE_VERSION, LATEST_SUPPORTED_JAVA_VERSION))
    assertTrue(isSupported(LATEST_GRADLE_VERSION, LATEST_SUPPORTED_JAVA_VERSION))

    assertFalse(isSupported(LATEST_GRADLE_VERSION, FUTURE_JAVA_VERSION))
    assertFalse(isSupported(FUTURE_GRADLE_VERSION, FUTURE_JAVA_VERSION))
  }

  fun `test suggesting latest gradle version for java version`() {
    assertEquals("4.10.3", suggestLatestSupportedGradleVersion(6))
    assertEquals("4.10.3", suggestLatestSupportedGradleVersion(7))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(8))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(11))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(17))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(19))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(20))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(21))
    assertEquals(LATEST_GRADLE_VERSION, suggestLatestSupportedGradleVersion(22))

    assertEquals(null, suggestLatestSupportedGradleVersion(FUTURE_JAVA_VERSION))
  }

  fun `test suggesting java version for gradle version`() {
    assertEquals(8, suggestLatestSupportedJavaVersion("0.9.2"))
    assertEquals(8, suggestLatestSupportedJavaVersion("2.0"))
    assertEquals(8, suggestLatestSupportedJavaVersion("3.0"))
    assertEquals(9, suggestLatestSupportedJavaVersion("4.3"))
    assertEquals(10, suggestLatestSupportedJavaVersion("4.7"))
    assertEquals(11, suggestLatestSupportedJavaVersion("5.0"))
    assertEquals(11, suggestLatestSupportedJavaVersion("5.1"))
    assertEquals(12, suggestLatestSupportedJavaVersion("5.4"))
    assertEquals(13, suggestLatestSupportedJavaVersion("6.0"))
    assertEquals(14, suggestLatestSupportedJavaVersion("6.3"))
    assertEquals(15, suggestLatestSupportedJavaVersion("6.7"))
    assertEquals(16, suggestLatestSupportedJavaVersion("7.1"))
    assertEquals(17, suggestLatestSupportedJavaVersion("7.2"))
    assertEquals(17, suggestLatestSupportedJavaVersion("7.4"))
    assertEquals(18, suggestLatestSupportedJavaVersion("7.5"))
    assertEquals(18, suggestLatestSupportedJavaVersion("7.5.1"))
    assertEquals(19, suggestLatestSupportedJavaVersion("7.6"))
    assertEquals(19, suggestLatestSupportedJavaVersion("8.2"))
    assertEquals(20, suggestLatestSupportedJavaVersion("8.3"))
    assertEquals(20, suggestLatestSupportedJavaVersion("8.4"))
    assertEquals(21, suggestLatestSupportedJavaVersion("8.5"))
    assertEquals(21, suggestLatestSupportedJavaVersion("8.6"))
    assertEquals(21, suggestLatestSupportedJavaVersion("8.7"))
    assertEquals(22, suggestLatestSupportedJavaVersion("8.8"))

    assertEquals(22, suggestLatestSupportedJavaVersion(FUTURE_GRADLE_VERSION))
  }

  fun `test suggesting oldest compatible gradle version for java version`() {
    assertEquals("4.5", suggestOldestSupportedGradleVersion(6))
    assertEquals("4.5", suggestOldestSupportedGradleVersion(7))
    assertEquals("4.5", suggestOldestSupportedGradleVersion(8))
    assertEquals("4.5", suggestOldestSupportedGradleVersion(9))
    assertEquals("4.7", suggestOldestSupportedGradleVersion(10))
    assertEquals("5.0", suggestOldestSupportedGradleVersion(11))
    assertEquals("5.4", suggestOldestSupportedGradleVersion(12))
    assertEquals("6.0", suggestOldestSupportedGradleVersion(13))
    assertEquals("6.3", suggestOldestSupportedGradleVersion(14))
    assertEquals("6.7", suggestOldestSupportedGradleVersion(15))
    assertEquals("7.0", suggestOldestSupportedGradleVersion(16))
    assertEquals("7.2", suggestOldestSupportedGradleVersion(17))
    assertEquals("7.5", suggestOldestSupportedGradleVersion(18))
    assertEquals("7.6", suggestOldestSupportedGradleVersion(19))
    assertEquals("8.3", suggestOldestSupportedGradleVersion(20))
    assertEquals("8.5", suggestOldestSupportedGradleVersion(21))
    assertEquals("8.8", suggestOldestSupportedGradleVersion(22))

    assertEquals(null, suggestOldestSupportedGradleVersion(FUTURE_JAVA_VERSION))
  }

  fun `test suggesting oldest compatible java version for gradle version`() {
    assertEquals(8, suggestOldestSupportedJavaVersion("2.0"))
    assertEquals(8, suggestOldestSupportedJavaVersion("3.0"))
    assertEquals(8, suggestOldestSupportedJavaVersion("5.0"))
    assertEquals(8, suggestOldestSupportedJavaVersion("5.1"))
    assertEquals(8, suggestOldestSupportedJavaVersion("7.1"))
    assertEquals(8, suggestOldestSupportedJavaVersion("7.2"))
    assertEquals(8, suggestOldestSupportedJavaVersion("7.5"))
    assertEquals(8, suggestOldestSupportedJavaVersion("7.6"))
    assertEquals(8, suggestOldestSupportedJavaVersion("8.0"))
    assertEquals(8, suggestOldestSupportedJavaVersion("8.8"))

    assertEquals(8, suggestOldestSupportedJavaVersion(FUTURE_GRADLE_VERSION))
  }
}