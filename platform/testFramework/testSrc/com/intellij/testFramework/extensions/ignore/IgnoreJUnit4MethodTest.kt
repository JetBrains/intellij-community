// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.extensions.ignore

import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import org.junit.Ignore
import org.junit.Test


/**
 * Tests to ensure [Ignore] annotation works for JUnit 4 methods.
 */
class IgnoreJUnit4MethodTest {
  @Test
  fun testShouldBeExecuted() {

  }

  /**
   * A test which should never be executed under TeamCity.
   * If executed, the test will immediately fail.
   */
  @Ignore(value = "IJI-1434")
  @Test
  fun testShouldNotBeExecuted() {
    if (IS_UNDER_TEAMCITY) {
      error("Test should not be executed under TeamCity")
    }
  }
}
