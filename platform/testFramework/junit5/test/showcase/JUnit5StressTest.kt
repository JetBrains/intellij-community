// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@StressTestApplication
class JUnit5StressTest {

  @Test
  fun ensureStress() {
    assertNotNull(ApplicationManager.getApplication(), "Application should exist as stress mode depends on application")
    assertTrue(ApplicationManagerEx.isInStressTest(), "App should be in stress mode")
  }

  // `@ParameterizedTest` (a `@TestTemplate`) is dispatched through InvocationInterceptor.interceptTestTemplateMethod,
  // which is a separate hook from interceptTestMethod. This guards that StressTestApplicationExtension wraps it too.
  @ParameterizedTest
  @ValueSource(ints = [1, 2])
  fun `ensure stress in parameterized test`(i: Int) {
    assertNotNull(ApplicationManager.getApplication(), "Application should exist as stress mode depends on application")
    assertTrue(ApplicationManagerEx.isInStressTest(), "App should be in stress mode in @ParameterizedTest (param=$i)")
  }
}
