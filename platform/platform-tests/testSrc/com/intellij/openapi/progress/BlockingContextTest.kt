// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class BlockingContextTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertNull(Cancellation.currentJob())
    val job = coroutineContext.job
    blockingContext {
      assertCurrentJobIsChildOf(job)
    }
    assertNull(Cancellation.currentJob())
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      val t = object : Throwable() {}
      val ce = assertThrows<PceCancellationException> {
        blockingContext {
          testNoExceptions()
          this@launch.cancel("", t)
          testExceptionsAndNonCancellableSection()
        }
      }
      //suppressed until this one is fixed: https://youtrack.jetbrains.com/issue/KT-52379
      @Suppress("AssertBetweenInconvertibleTypes")
      assertSame(t, ce.cause.cause?.cause)
    }
  }

  @Test
  fun `checkCancelledEvenWithPCEDisabled checks job`(): Unit = timeoutRunBlocking {
    launch {
      blockingContext {
        assertDoesNotThrow {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
        this@launch.cancel()
        assertThrows<CeProcessCanceledException> {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
        Cancellation.computeInNonCancelableSection<Unit, Exception> {
          assertDoesNotThrow {
            ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
          }
        }
        assertThrows<CeProcessCanceledException> {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
        ProgressManager.getInstance().computeInNonCancelableSection<Unit, Exception> {
          assertDoesNotThrow {
            ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
          }
        }
        assertThrows<CeProcessCanceledException> {
          ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(null)
        }
      }
    }
  }
}
