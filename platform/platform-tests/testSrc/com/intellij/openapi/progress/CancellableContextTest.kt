// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.Cancellation.computeInNonCancelableSection
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestApplication
class CancellableContextTest {

  @Test
  fun empty() {
    assertFalse(isInCancellableContext())
  }

  @Test
  fun job(): Unit = timeoutRunBlocking {
    assertTrue(isInCancellableContext())
    blockingContext {
      assertTrue(isInCancellableContext())
      computeInNonCancelableSection<Unit, Exception> {
        assertFalse(isInCancellableContext())
      }
      assertTrue(isInCancellableContext())
    }
    assertTrue(isInCancellableContext())
  }

  @Test
  fun indicator() = timeoutRunBlocking {
    assertTrue(isInCancellableContext())
    coroutineToIndicator {
      assertTrue(isInCancellableContext())
      computeInNonCancelableSection<Unit, Exception> {
        assertFalse(isInCancellableContext())
      }
      assertTrue(isInCancellableContext())
    }
    assertTrue(isInCancellableContext())
  }
}
