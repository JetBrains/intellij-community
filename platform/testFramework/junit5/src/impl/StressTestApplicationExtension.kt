// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.application.ex.ApplicationManagerEx
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor

/**
 * A JUnit5 extension which runs tests inside this class with the [ApplicationManagerEx.isInStressTest]`=true` flag.
 * Usage example:
 * ```
 * @StressTestApplication
 * class MyTest {
 * }
 * ```
 * See [com.intellij.testFramework.junit5.StressTestApplication] annotation.
 *
 * The flag is on from [beforeAll] until JUnit5 closes the store of the class context, after all `afterAll` callbacks.
 * Thus the setup and the teardown of the [com.intellij.testFramework.junit5.fixture.TestFixture] fields observe the flag too.
 * The fixture extension does them in its callbacks, not in invocations.
 * [com.intellij.testFramework.junit5.StressTestApplication] registers this extension first, so [beforeAll] runs before the fixture setup.
 * The extension saves the flag in [beforeAll] and restores it when the store closes, so the flag does not leak to the next class.
 * The extension starts the shared application before it sets the flag, so the start of the application does not depend on the first class.
 *
 * Every invocation JUnit5 routes through [InvocationInterceptor] is also wrapped in [ApplicationManagerEx.runInStressTest],
 * because [AbstractInvocationInterceptor] funnels all of its hooks into a single [intercept].
 * This covers the test class constructor of a `PER_CLASS` lifecycle, which JUnit5 calls before [beforeAll].
 *
 * For JUnit4, use the `com.intellij.testFramework.StressTestRule` rule instead.
 */
@TestOnly
internal class StressTestApplicationExtension : AbstractInvocationInterceptor(), BeforeAllCallback {
  override fun beforeAll(context: ExtensionContext) {
    context.testApplication().getOrThrow()
    context.getStore(ExtensionContext.Namespace.create(StressTestApplicationExtension::class.java))
      .put(StressMode::class.java, StressMode())
  }

  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    var result: Any? = null
    ApplicationManagerEx.runInStressTest<Throwable>(true) {
      result = invocation.proceed()
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
}

/** Sets the stress mode on, and restores the previous value when JUnit5 closes the store that holds it. */
@TestOnly
private class StressMode : AutoCloseable {
  private val previous = ApplicationManagerEx.isInStressTest()

  init {
    @Suppress("DEPRECATION")
    ApplicationManagerEx.setInStressTest(true)
  }

  override fun close() {
    @Suppress("DEPRECATION")
    ApplicationManagerEx.setInStressTest(previous)
  }
}
