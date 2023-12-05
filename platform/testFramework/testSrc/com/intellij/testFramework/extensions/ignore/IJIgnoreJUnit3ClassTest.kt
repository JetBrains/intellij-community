// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.extensions.ignore

import com.intellij.idea.IJIgnore
import junit.framework.TestCase


/**
 * Tests to ensure [IJIgnore] annotation works for JUnit 3 test class.
 */
@IJIgnore(issue = "IJI-1434")
class IJIgnoreJUnit3ClassTest : TestCase() {
  /**
   * A test which should never be executed.
   * If executed, the test will immediately fail.
   */
  fun testShouldNotBeExecuted() {
    error("Test should not be executed")
  }
}
