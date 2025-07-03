// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency

import org.junit.jupiter.api.Test
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ThreadContextTest {

  @Test
  fun `reset context`() {
    assertNull(currentThreadContextOrNull())
    installThreadContext(EmptyCoroutineContext).use {
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
    installThreadContext(outerElement1).use {
      assertSame(currentThreadContext(), outerElement1)

      val innerElement1 = TestElement("inner1")
      installThreadContext(innerElement1, replace = true).use {
        assertSame(currentThreadContext(), innerElement1)
      }
      assertSame(currentThreadContext(), outerElement1)

      val innerElement2 = TestElement2("inner2")
      installThreadContext(innerElement2, replace = true).use {
        assertSame(currentThreadContext(), innerElement2)
      }
      assertSame(currentThreadContext(), outerElement1)
    }
  }
}
