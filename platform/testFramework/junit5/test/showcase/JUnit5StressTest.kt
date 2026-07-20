// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@StressTestApplication
class JUnit5StressTest {

  // Lifecycle methods must observe stress mode too (guards against regressions where only test-method invocations are wrapped,
  // leaving @BeforeAll/@BeforeEach/@AfterEach/@AfterAll with isInStressTest()=false).
  companion object {
    @JvmStatic
    @BeforeAll
    fun ensureStressInBeforeAll() {
      assertTrue(ApplicationManagerEx.isInStressTest(), "@BeforeAll should run in stress mode")
    }

    @JvmStatic
    @AfterAll
    fun ensureStressInAfterAll() {
      assertTrue(ApplicationManagerEx.isInStressTest(), "@AfterAll should run in stress mode")
    }
  }

  @BeforeEach
  fun ensureStressInBeforeEach() {
    assertTrue(ApplicationManagerEx.isInStressTest(), "@BeforeEach should run in stress mode")
  }

  @AfterEach
  fun ensureStressInAfterEach() {
    assertTrue(ApplicationManagerEx.isInStressTest(), "@AfterEach should run in stress mode")
  }

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
