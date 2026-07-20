// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.application.ex.ApplicationManagerEx
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.DynamicContainer
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.jvm.optionals.getOrNull

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
 * For JUnit4, use [com.intellij.testFramework.StressTestRule] extension instead.
 */
@TestOnly
class StressTestApplicationExtension : InvocationInterceptor {
  /**
   * The lifecycle methods below ([interceptTestClassConstructor], `@BeforeAll`/`@BeforeEach`/`@AfterEach`/`@AfterAll`) are run in
   * stress mode too. Setup and teardown of a stress test must observe the same [ApplicationManagerEx.isInStressTest]`=true` state as
   * the test body, otherwise fixtures behave differently from the code under test (e.g. the test framework's permanent debug log level
   * is only lifted while in stress mode). This restores the class-scoped coverage the previous `BeforeAllCallback`-based implementation
   * provided, but safely via [ApplicationManagerEx.runInStressTest]'s save/restore instead of the leaky `setInStressTest`.
   */
  override fun <T> interceptTestClassConstructor(
    invocation: InvocationInterceptor.Invocation<T?>,
    invocationContext: ReflectiveInvocationContext<Constructor<T>>,
    extensionContext: ExtensionContext?,
  ): T? {
    var r: T? = null
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      r = super.interceptTestClassConstructor(invocation, invocationContext, extensionContext)
    }
    return r
  }

  override fun interceptBeforeAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptBeforeAllMethod(invocation, invocationContext, extensionContext)
    }
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptBeforeEachMethod(invocation, invocationContext, extensionContext)
    }
  }

  override fun interceptAfterEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptAfterEachMethod(invocation, invocationContext, extensionContext)
    }
  }

  override fun interceptAfterAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptAfterAllMethod(invocation, invocationContext, extensionContext)
    }
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptTestMethod(invocation, invocationContext, extensionContext)
    }
  }

  /**
   * intercept `@TestTemplate`-based invocations (e.g. `@ParameterizedTest`, `@RepeatedTest`), which JUnit5 dispatches through
   * [interceptTestTemplateMethod] rather than [interceptTestMethod]. Without this override, parameterized stress tests would run
   * with [ApplicationManagerEx.isInStressTest]`=false`.
   */
  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ) {
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
    }
  }

  /**
   * intercept creation of a factory method, and wrap its evaluation return value with [ApplicationManagerEx.runInStressTest] so that
   * all returned [DynamicTest]s are run in a stress mode too.
   * */
  override fun <T> interceptTestFactoryMethod(
    invocation: InvocationInterceptor.Invocation<T?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext?,
  ): T? {
    var r: T? = null
    ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
      r = super.interceptTestFactoryMethod(invocation, invocationContext, extensionContext)
    }
    return wrapTest(r)
  }

  private fun <T> wrapTest(t: T?): T? = when (t) {
    is Iterable<*> -> t.map { wrapTest(it) }
    is DynamicContainer -> DynamicContainer.dynamicContainer(t.displayName, t.children.map { wrapTest(it)})
    is DynamicTest -> DynamicTest.dynamicTest(t.displayName, t.testSourceUri.getOrNull()) {
      ApplicationManagerEx.runInStressTest<RuntimeException>(true) {
        t.executable.execute()
      }
    }
    else -> t
  } as T?
}
