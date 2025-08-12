// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diagnostic

import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.Test

class GetOrHandleExceptionTest {
  @Test
  fun `CancellationException is rethrown with an added stack`() {
    var handled = false
    val thrown = catchThrowable {
      runCatching {
        throw CancellationException("My message")
      }.getOrHandleException { handled = true }
    }
    assertThat(handled).isFalse()
    assertThat(thrown)
      .isInstanceOf(CancellationException::class.java)
      .hasMessage("My message")
    assertThat(thrown.suppressedExceptions).hasSize(1)
    assertThat(thrown.suppressedExceptions.single())
      .hasStackTraceContaining("GetOrHandleExceptionTest")
  }

  @Test
  fun `ProcessCanceledException is rethrown with an added stack`() {
    var handled = false
    val cause = Throwable("Some cause")
    val thrown = catchThrowable {
      runCatching {
        throw ProcessCanceledException(cause)
      }.getOrHandleException { handled = true }
    }
    assertThat(handled).isFalse()
    assertThat(thrown)
      .isInstanceOf(ProcessCanceledException::class.java)
      .hasCause(cause)
      .hasMessage(cause.toString())
    assertThat(thrown.suppressedExceptions).hasSize(1)
    assertThat(thrown.suppressedExceptions.single())
      .hasStackTraceContaining("GetOrHandleExceptionTest")
  }

  @Test
  fun `ControlFlowException is rethrown with an added stack`() {
    var handled = false
    val thrown = catchThrowable {
      runCatching {
        throw MyControlFlowException()
      }.getOrHandleException { handled = true }
    }
    assertThat(handled).isFalse()
    assertThat(thrown)
      .isInstanceOf(MyControlFlowException::class.java)
    assertThat(thrown.suppressedExceptions).hasSize(1)
    assertThat(thrown.suppressedExceptions.single())
      .hasStackTraceContaining("GetOrHandleExceptionTest")
  }

  @Test
  fun `regular exceptions are handled`() {
    var handled: Throwable? = null
    val thrown = Throwable("My message")
    runCatching {
      throw thrown
    }.getOrHandleException { exception ->
      handled = exception
    }
    assertThat(handled).isSameAs(thrown)
  }

  @Test
  fun `the handler can be suspending`() {
    runBlocking {
      var handled: Throwable? = null
      val thrown = Throwable("My message")
      runCatching {
        throw thrown
      }.getOrHandleException { exception ->
        delay(0)
        handled = exception
      }
      assertThat(handled).isSameAs(thrown)
    }
  }
}

private class MyControlFlowException : Throwable(), ControlFlowException
