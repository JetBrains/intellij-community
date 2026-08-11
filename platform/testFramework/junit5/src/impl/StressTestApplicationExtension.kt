// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.application.ex.ApplicationManagerEx
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor

/**
 * A JUnit5 extension which runs tests inside this class with the [ApplicationManagerEx.isInStressTest]`=true` flag,
 * using [ApplicationManagerEx.runInStressTest] method.
 * Usage example:
 * ```
 * @StressTestApplication
 * class MyTest {
 * }
 * ```
 * See [com.intellij.testFramework.junit5.StressTestApplication] annotation.
 *
 * Every invocation JUnit5 routes through [InvocationInterceptor] is wrapped, because [AbstractInvocationInterceptor] funnels all of its
 * hooks into a single [intercept]: the test class constructor, `@BeforeAll`/`@BeforeEach`/`@AfterEach`/`@AfterAll`, plain `@Test`s,
 * `@TestTemplate`-based ones (`@ParameterizedTest`, `@RepeatedTest`), `@TestFactory` methods and each dynamic test they produce.
 * Setup and teardown of a stress test must observe the same [ApplicationManagerEx.isInStressTest]`=true` state as the test body,
 * otherwise fixtures behave differently from the code under test (e.g. the test framework's permanent debug log level is only lifted
 * while in stress mode). [ApplicationManagerEx.runInStressTest] saves and restores the flag, unlike the leaky `setInStressTest`.
 *
 * For JUnit4, use the `com.intellij.testFramework.StressTestRule` rule instead.
 */
@TestOnly
internal class StressTestApplicationExtension : AbstractInvocationInterceptor() {
  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    var result: Any? = null
    ApplicationManagerEx.runInStressTest<Throwable>(true) {
      result = invocation.proceed()
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
}
