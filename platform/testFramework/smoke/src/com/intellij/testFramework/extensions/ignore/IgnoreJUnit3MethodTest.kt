// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.extensions.ignore

import com.intellij.idea.IgnoreJUnit3
import com.intellij.testFramework.UsefulTestCase.IS_UNDER_TEAMCITY
import junit.framework.TestCase


/**
 * Tests to ensure [IgnoreJUnit3] annotation works for JUnit 3 methods.
 */
class IgnoreJUnit3MethodTest : TestCase() {
  fun testShouldBeExecuted() {

  }

  /**
   * A test which should never be executed under TeamCity.
   * If executed, the test will immediately fail.
   */
  @IgnoreJUnit3(reason = "IJI-1434")
  fun testShouldNotBeExecuted() {
    if (IS_UNDER_TEAMCITY) {
      error("Test should not be executed under TeamCity")
    }
  }
}
