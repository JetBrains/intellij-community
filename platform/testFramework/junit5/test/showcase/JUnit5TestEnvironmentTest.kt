// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.testFramework.common.isTestEnvironmentInitialized
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class JUnit5TestEnvironmentTest {

  @Test
  fun `test environment is initialized`() {
    Assertions.assertTrue(isTestEnvironmentInitialized) {
      "com.intellij.testFramework.common.TestEnvironmentKt#initializeTestEnvironment was not invoked by the session listener"
    }
  }
}
