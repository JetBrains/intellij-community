// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.testFramework.ApplicationExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertSame

class ThreadContextTest {

  companion object {
    @RegisterExtension
    @JvmField
    val applicationExtension = ApplicationExtension()
  }

  private fun assertContextElements(context: CoroutineContext, vararg elements: CoroutineContext.Element) {
    for (element in elements) {
      assertSame(element, context[element.key])
    }
    val contextSize = context.fold(0) { acc, _ ->
      acc + 1
    }
    assertEquals(elements.size, contextSize)
  }

  @Test
  fun `withThreadContext updates context`() {
    val outerElement1 = TestElement("outer1")
    withThreadContext(outerElement1).use {
      assertContextElements(currentThreadContext(), outerElement1)

      val innerElement1 = TestElement("inner1")
      withThreadContext(innerElement1).use {
        assertContextElements(currentThreadContext(), innerElement1)
      }
      assertContextElements(currentThreadContext(), outerElement1)

      val innerElement2 = TestElement2("inner2")
      withThreadContext(innerElement2).use {
        assertContextElements(currentThreadContext(), outerElement1, innerElement2)
      }
      assertContextElements(currentThreadContext(), outerElement1)
    }
  }

  @Test
  fun `resetThreadContext replaces context`() {
    val outerElement1 = TestElement("outer1")
    resetThreadContext(outerElement1).use {
      assertSame(currentThreadContext(), outerElement1)

      val innerElement1 = TestElement("inner1")
      resetThreadContext(innerElement1).use {
        assertSame(currentThreadContext(), innerElement1)
      }
      assertSame(currentThreadContext(), outerElement1)

      val innerElement2 = TestElement2("inner2")
      resetThreadContext(innerElement2).use {
        assertSame(currentThreadContext(), innerElement2)
      }
      assertSame(currentThreadContext(), outerElement1)
    }
  }

  @Test
  fun `resetThreadContext in coroutine`() {
    val outerElement = TestElement("outer")
    withThreadContext(outerElement).use {
      runBlocking {
        assertSame(outerElement, currentThreadContext())
        resetThreadContext().use {
          assertSame(coroutineContext, currentThreadContext())
        }
        assertSame(outerElement, currentThreadContext())
      }
    }
  }
}
