// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertSame

@TestApplication
class ThreadContextTest {

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
  fun `resetThreadContext resets context`() {
    assertDoesNotThrow {
      checkUninitializedThreadContext()
    }
    withThreadContext(EmptyCoroutineContext).use {
      assertThrows<IllegalStateException> {
        checkUninitializedThreadContext()
      }
      resetThreadContext().use {
        assertDoesNotThrow {
          checkUninitializedThreadContext()
        }
      }
      assertThrows<IllegalStateException> {
        checkUninitializedThreadContext()
      }
    }
    assertDoesNotThrow {
      checkUninitializedThreadContext()
    }
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
  fun `replaceThreadContext replaces context`() {
    val outerElement1 = TestElement("outer1")
    replaceThreadContext(outerElement1).use {
      assertSame(currentThreadContext(), outerElement1)

      val innerElement1 = TestElement("inner1")
      replaceThreadContext(innerElement1).use {
        assertSame(currentThreadContext(), innerElement1)
      }
      assertSame(currentThreadContext(), outerElement1)

      val innerElement2 = TestElement2("inner2")
      replaceThreadContext(innerElement2).use {
        assertSame(currentThreadContext(), innerElement2)
      }
      assertSame(currentThreadContext(), outerElement1)
    }
  }
}
