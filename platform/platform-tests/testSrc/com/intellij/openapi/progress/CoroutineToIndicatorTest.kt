// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.platform.util.progress.ExpectedState
import com.intellij.platform.util.progress.progressReporterTest
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CoroutineToIndicatorTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())

    val modality = ModalityStateEx()

    withContext(modality.asContextElement()) {
      assertSame(ModalityState.nonModal(), ModalityState.defaultModalityState())
      coroutineToIndicator {
        assertNull(Cancellation.currentJob())
        assertNotNull(ProgressManager.getGlobalProgressIndicator())
        assertSame(modality, ModalityState.defaultModalityState())
      }
      assertSame(ModalityState.nonModal(), ModalityState.defaultModalityState())
    }

    assertNull(Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())
  }

  @Test
  fun cancellation(): Unit = timeoutRunBlocking {
    launch {
      assertThrows<CancellationException> {
        coroutineToIndicator {
          ProgressManager.checkCanceled()
          coroutineContext.job.cancel()
          throw assertThrows<ProcessCanceledException> {
            ProgressManager.checkCanceled()
          }
        }
      }
    }
  }

  @Test
  fun rethrow(): Unit = timeoutRunBlocking {
    testRunUnderIndicatorRethrow(object : Throwable() {})
    testRunUnderIndicatorRethrow(CancellationException()) // manual CE
    testRunUnderIndicatorRethrow(ProcessCanceledException()) // manual PCE
  }

  private suspend inline fun <reified T : Throwable> testRunUnderIndicatorRethrow(t: T) {
    val thrown = assertThrows<T> {
      coroutineToIndicator {
        throw t
      }
    }
    assertSame(t, thrown)
  }

  private suspend inline fun testRunUnderIndicatorRethrow(t: ProcessCanceledException) {
    val thrown = assertThrows<PceCancellationException> {
      coroutineToIndicator {
        throw t
      }
    }
    assertSame(t, thrown.cause)
  }

  @Test
  fun `delegates reporting to context reporter`() {
    progressReporterTest(
      ExpectedState(text = "Hello", details = null, fraction = null),
      ExpectedState(text = "Hello", details = "World", fraction = null),
      ExpectedState(text = "Hello", details = "World", fraction = 0.42),
      ExpectedState(text = null, details = "World", fraction = 0.42),
      ExpectedState(text = null, details = "World", fraction = null),
    ) {
      coroutineToIndicator {
        ProgressManager.progress("Hello", "World")
        val indicator = ProgressManager.getInstance().progressIndicator
        indicator.fraction = 0.42
        indicator.text = null
        indicator.isIndeterminate = true
      }
    }
  }
}
