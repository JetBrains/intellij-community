// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.extensions.ignore

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test


/**
 * Tests to ensure [Disabled] annotation works for JUnit 5 methods.
 */
class DisabledJUnit5MethodTest {
  @Test
  fun testShouldBeExecuted() {

  }

  /**
   * A test which should never be executed.
   * If executed, the test will immediately fail.
   */
  @Disabled("IJI-1434")
  @Test
  fun testShouldNotBeExecuted() {
    error("Test should not be executed")
  }
}
