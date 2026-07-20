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
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@StressTestApplication
class JUnit5StressTest {

  // The test class constructor is a separate InvocationInterceptor hook (interceptTestClassConstructor), so instance initialization
  // must observe stress mode as well — fields initialized here are part of the fixture the test body relies on.
  init {
    assertTrue(ApplicationManagerEx.isInStressTest(), "Test class constructor should run in stress mode")
  }

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

  // A `@TestFactory` produces dynamic tests which JUnit5 executes through yet another hook, interceptDynamicTest: the factory method
  // itself running in stress mode says nothing about the tests it returns. Both a flat DynamicTest and one nested in a DynamicContainer
  // are checked, since containers are expanded separately from the factory invocation.
  @TestFactory
  fun `ensure stress in dynamic tests`(): List<DynamicNode> {
    assertTrue(ApplicationManagerEx.isInStressTest(), "@TestFactory method should run in stress mode")
    return listOf(
      DynamicTest.dynamicTest("flat dynamic test") {
        assertNotNull(ApplicationManager.getApplication(), "Application should exist as stress mode depends on application")
        assertTrue(ApplicationManagerEx.isInStressTest(), "DynamicTest should run in stress mode")
      },
      DynamicContainer.dynamicContainer("dynamic container", listOf(
        DynamicTest.dynamicTest("nested dynamic test") {
          assertTrue(ApplicationManagerEx.isInStressTest(), "DynamicTest nested in a DynamicContainer should run in stress mode")
        }
      )),
    )
  }
}
