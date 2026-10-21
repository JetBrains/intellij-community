// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import kotlinx.coroutines.ThreadContextElement
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ThreadContextTest {

  @Test
  fun `reset context`() {
    assertNull(currentThreadContextOrNull())
    installThreadContext(EmptyCoroutineContext) {
      assertNotNull(currentThreadContextOrNull())
      resetThreadContext() {
        assertNull(currentThreadContextOrNull())
      }
      assertNotNull(currentThreadContextOrNull())
    }
    assertNull(currentThreadContextOrNull())
  }

  @Test
  fun `replace context`() {
    val outerElement1 = TestElement("outer1")
    installThreadContext(outerElement1) {
      assertSame(currentThreadContext(), outerElement1)

      val innerElement1 = TestElement("inner1")
      installThreadContext(innerElement1, replace = true) {
        assertSame(currentThreadContext(), innerElement1)
      }
      assertSame(currentThreadContext(), outerElement1)

      val innerElement2 = TestElement2("inner2")
      installThreadContext(innerElement2, replace = true) {
        assertSame(currentThreadContext(), innerElement2)
      }
      assertSame(currentThreadContext(), outerElement1)
    }
  }

  class MyThreadContextElement(val ref: AtomicInteger) : ThreadContextElement<Int> {
    companion object Key : CoroutineContext.Key<MyThreadContextElement>

    override val key: CoroutineContext.Key<MyThreadContextElement> = Key

    override fun updateThreadContext(context: CoroutineContext): Int {
      return ref.incrementAndGet()
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: Int) {
      assertTrue { ref.get() == oldState }
      ref.incrementAndGet()
    }
  }

  @Test
  fun `installThreadContext triggers ThreadContextElement`() {
    val elem = MyThreadContextElement(AtomicInteger(0))
    installThreadContext(elem) {
      assertEquals(1, elem.ref.get())
    }
    assertEquals(2, elem.ref.get())
  }
}
