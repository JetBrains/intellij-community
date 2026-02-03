// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.seconds

class NonCancellableSectionTest {

  @Test
  fun `checkCancelled throws PCE if current job is cancelled`(): Unit = runBlocking {
    launch {
      coroutineContext.cancel()
      assertThrows<ProcessCanceledException>("checkCancelled() MUST throw (P)CE if current job is cancelled") {
        Cancellation.checkCancelled()
      }
    }
  }

  @Test
  fun `checkCancelled NOT throw PCE inside non-cancellable section even if current job is cancelled `(): Unit = runBlocking {
    launch {
      coroutineContext.cancel()
      Cancellation.computeInNonCancelableSection<Unit, Exception> {
        assertDoesNotThrow("checkCancelled() must NOT throw (P)CE if called inside a non-cancellable section") {
          Cancellation.checkCancelled()
        }
      }
    }
  }

  @Test
  fun `PCE could be thrown from non-cancellable section if task is cancelled from outside`(): Unit = timeoutRunBlocking(10.seconds) {
    launch {
      val longTask = async { delay(Integer.MAX_VALUE.toLong()) }

      longTask.cancel()

      assertThrows<ProcessCanceledException>("task is cancelled from outside, so waiting _must_ throw (P)CE even within non-cancellable section") {
        Cancellation.computeInNonCancelableSection<Unit, Exception> {
          runBlockingCancellable {
            longTask.await()
          }
        }
      }
    }
  }
}
