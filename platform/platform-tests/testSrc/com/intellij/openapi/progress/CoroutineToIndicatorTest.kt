// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.ModalityStateEx
import com.intellij.platform.util.progress.ExpectedState
import com.intellij.platform.util.progress.progressReporterTest
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

class CoroutineToIndicatorTest : CancellationTest() {

  @Test
  fun context(): Unit = timeoutRunBlocking {
    assertEquals(coroutineContext.job, Cancellation.currentJob())
    assertNull(ProgressManager.getGlobalProgressIndicator())

    val modality = ModalityStateEx()

    withContext(modality.asContextElement()) {
      assertSame(modality, ModalityState.defaultModalityState())
      coroutineToIndicator {
        assertNotNull(Cancellation.currentJob())
        assertNotNull(ProgressManager.getGlobalProgressIndicator())
        assertSame(modality, ModalityState.defaultModalityState())
      }
      assertSame(modality, ModalityState.defaultModalityState())
    }

    assertEquals(coroutineContext.job, Cancellation.currentJob())
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
    val thrown = assertThrows<ProcessCanceledException> {
      coroutineToIndicator {
        throw t
      }
    }
    assertSame(t, thrown)
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
