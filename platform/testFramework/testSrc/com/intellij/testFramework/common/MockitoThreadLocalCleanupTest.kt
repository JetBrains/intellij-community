// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * Each test runs [MockitoThreadLocalCleanup] in a class loader of its own.
 * That class loader starts with no loaded Mockito class, as a new JVM does.
 */
class MockitoThreadLocalCleanupTest {
  @Test
  fun `the cleanup loads no Mockito class when Mockito was not used`() {
    val loader = IsolatedClassLoader()

    loader.callStatic(MockitoThreadLocalCleanup::class.java.name, "clearMockitoState")

    assertThat(MOCKITO_CLASSES.filter { loader.hasLoadedClass(it) }).isEmpty()
  }

  @Test
  fun `the cleanup clears the thread state and the inline mocks when Mockito was used`() {
    val loader = IsolatedClassLoader()
    // Mockito loads its plugins through the context class loader.
    withContextClassLoader(loader) {
      val mockito = loader.loadClass("org.mockito.Mockito")
      // The inline mock maker gives a mock of a class, not of an interface.
      val targetClass = loader.loadClass(MockTarget::class.java.name)
      val mock = mockito.getMethod("mock", Class::class.java).invoke(null, targetClass)
      // `verify` with no call on the mock leaves an unfinished verification in the thread state.
      mockito.getMethod("verify", Any::class.java).invoke(null, mock)

      loader.callStatic(MockitoThreadLocalCleanup::class.java.name, "clearMockitoState")

      // `validateMockitoUsage` throws `UnfinishedVerificationException` when the thread state was not reset.
      mockito.getMethod("validateMockitoUsage").invoke(null)
      // `clearInlineMocks` disables each inline mock.
      assertThatThrownBy { targetClass.getMethod("call").invoke(mock) }
        .cause()
        .isInstanceOf(loader.loadClass("org.mockito.exceptions.misusing.DisabledMockException"))
    }
  }
}

internal open class MockTarget {
  open fun call(): String = "real"
}

private val MOCKITO_CLASSES = listOf(
  "org.mockito.Mockito",
  "org.mockito.internal.progress.ThreadSafeMockingProgress",
  "org.mockito.internal.framework.DefaultMockitoFramework",
  "net.bytebuddy.ByteBuddy",
)

private fun withContextClassLoader(loader: ClassLoader, action: () -> Unit) {
  val thread = Thread.currentThread()
  val previous = thread.contextClassLoader
  thread.contextClassLoader = loader
  try {
    action()
  }
  finally {
    thread.contextClassLoader = previous
  }
}
