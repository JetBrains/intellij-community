// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

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
  val jce = assertThrows<JobCanceledException> {
    ProgressManager.checkCanceled()
  }
  assertSame(ce, jce.cause)
  throw jce
}

private fun testNonCancellableSection() {
  ProgressManager.getInstance().executeNonCancelableSection {
    assertThrows<JobCanceledException> {
      Cancellation.checkCancelled()
    }
    assertDoesNotThrow {
      ProgressManager.checkCanceled()
    }
  }
}

fun testExceptionsAndNonCancellableSection(): Nothing {
  assertThrows<JobCanceledException> {
    testExceptions()
  }
  testNonCancellableSection()
  throw assertThrows<JobCanceledException> {
    testExceptions()
  }
}

fun testEnsureCurrentJobRethrow() {
  testEnsureCurrentJobRethrow(object : Throwable() {})
  testEnsureCurrentJobRethrow(CancellationException()) // manual CE
  testEnsureCurrentJobRethrow(ProcessCanceledException()) // manual PCE
}

private inline fun <reified T : Throwable> testEnsureCurrentJobRethrow(t: T) {
  val thrown = assertThrows<T> {
    ensureCurrentJob {
      throw t
    }
  }
  assertSame(t, thrown)
}
