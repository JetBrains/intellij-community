// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.extensions.ignore

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


/**
 * Tests to ensure [Disabled] annotation works for JUnit 5 test class.
 */
@Disabled("IJI-1434")
class DisabledJUnit5ClassTest {
  /**
   * A test which should never be executed.
   * If executed, the test will immediately fail.
   */
  @Test
  fun testShouldNotBeExecuted() {
    error("Test should not be executed")
  }
}
