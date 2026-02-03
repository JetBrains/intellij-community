// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.currentThreadContextOrNull
import com.intellij.concurrency.installThreadContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertNotNull
import kotlin.test.assertNull

fun testNoExceptions() {
  assertDoesNotThrow {
    Cancellation.checkCancelled()
    ProgressManager.checkCanceled()
  }
}

fun testExceptions(): Nothing {
  val ce = assertThrows<CancellationException> {
    requireNotNull(Cancellation.currentJob()).ensureActive()
  }
  val jce = assertThrows<CeProcessCanceledException> {
    Cancellation.checkCancelled()
  }
  assertSame(ce, jce.cause)
  throw jce
}

private fun testNonCancellableSection() {
  ProgressManager.getInstance().executeNonCancelableSection {
    assertDoesNotThrow {
      Cancellation.checkCancelled()
    }
    assertDoesNotThrow {
      ProgressManager.checkCanceled()
    }
  }
}

fun testExceptionsAndNonCancellableSection(): Nothing {
  assertThrows<CeProcessCanceledException> {
    testExceptions()
  }
  testNonCancellableSection()
  throw assertThrows<CeProcessCanceledException> {
    testExceptions()
  }
}

fun testPrepareThreadContextRethrow() {
  testPrepareThreadContextRethrow(object : Throwable() {})
  testPrepareThreadContextRethrow(CancellationException()) // manual CE
  testPrepareThreadContextRethrow(ProcessCanceledException()) // manual PCE
}

private inline fun <reified T : Throwable> testPrepareThreadContextRethrow(t: T) {
  val thrown = assertThrows<T> {
    prepareThreadContextTest {
      throw t
    }
  }
  assertSame(t, thrown)
}

fun <T> prepareThreadContextTest(action: (Job) -> T): T {
  return prepareThreadContext { ctx ->
    assertNull(currentThreadContextOrNull())
    assertNull(ProgressManager.getGlobalProgressIndicator())
    val job = assertNotNull(ctx[Job])
    installThreadContext(ctx) {
      assertSame(job, Cancellation.currentJob())
      action(job)
    }
  }
}
