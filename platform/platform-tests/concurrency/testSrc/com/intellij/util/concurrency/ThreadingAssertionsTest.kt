// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.openapi.diagnostic.RuntimeExceptionWithAttachments
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine


@TestApplication
internal class ThreadingAssertionsTest {

  @ParameterizedTest
  @EnumSource(ThreadingAnnotation::class)
  fun javaThreadingAnnotation(annotation: ThreadingAnnotation): Unit = timeoutRunBlocking {
    inInvalidContext(annotation) {
      if (annotation == ThreadingAnnotation.READ_LOCK) {
        assertLoggedError(annotation, annotation.javaCall)
      }
      else {
        val error = runCatching { annotation.javaCall() }.exceptionOrNull()
        assertThat(error).isInstanceOf(RuntimeExceptionWithAttachments::class.java).hasMessageContaining(annotation.message)
      }
    }
  }

  @ParameterizedTest
  @EnumSource(ThreadingAnnotation::class)
  fun kotlinThreadingAnnotation(annotation: ThreadingAnnotation): Unit = timeoutRunBlocking {
    inInvalidContext(annotation) {
      assertLoggedError(annotation, annotation.kotlinCall)
    }
  }

  @Test
  fun doubleThreadingAnnotation(): Unit = timeoutRunBlocking {
    val edtAnnotation = ThreadingAnnotation.EDT
    val backgroundAnnotation = ThreadingAnnotation.BACKGROUND_THREAD
    inInvalidContext(edtAnnotation) {
      assertLoggedError(edtAnnotation, KotlinThreadingAnnotationChecks::severalAnnotations)
    }
    inInvalidContext(backgroundAnnotation) {
      assertLoggedError(backgroundAnnotation, KotlinThreadingAnnotationChecks::severalAnnotations)
    }
  }

  private suspend fun inInvalidContext(annotation: ThreadingAnnotation, action: () -> Unit) {
    withContext(Dispatchers.Default) {
      when (annotation) {
        ThreadingAnnotation.BACKGROUND_THREAD -> withContext(Dispatchers.UI) { action() }
        ThreadingAnnotation.READ_LOCK_ABSENCE -> readAction { action() }
        else -> action()
      }
    }
  }

  private fun assertLoggedError(annotation: ThreadingAnnotation, action: () -> Boolean) {
    val error = LoggedErrorProcessor.executeAndReturnLoggedError {
      assertThat(action()).isTrue()
    }
    assertThat(error).isNotNull.hasMessageContaining(annotation.message)
  }

  @Test
  fun softAssertBackgroundThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.EDT) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertBackgroundThread()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Access from Event Dispatch Thread (EDT) is not allowed")
  }

  @Test
  fun assertBackgroundThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.EDT) {
      runCatching {
        ThreadingAssertions.assertBackgroundThread()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access from Event Dispatch Thread (EDT) is not allowed")
  }

  @Test
  fun softAssertEventDispatchThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertEventDispatchThread()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from Event Dispatch Thread (EDT) only")
  }

  @Test
  fun assertEventDispatchThread(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertEventDispatchThread()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from Event Dispatch Thread (EDT) only")
  }

  @Test
  fun softAssertReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertReadAccess()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Read access is allowed from inside read-action only")
  }

  @Test
  fun assertReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertReadAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Read access is allowed from inside read-action only")
  }

  @Test
  fun softAssertWriteAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      LoggedErrorProcessor.executeAndReturnLoggedError {
        ThreadingAssertions.softAssertWriteAccess()
      }
    }
    assertThat(err).isNotNull.hasMessageContaining("Write access is allowed inside write-action only")
  }

  @Test
  fun softAssertWriteAccessInsideWriteAction(): Unit = timeoutRunBlocking {
    writeAction {
      ThreadingAssertions.softAssertWriteAccess()
    }
  }

  @Test
  fun assertWriteAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertWriteAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Write access is allowed inside write-action only")
  }

  @Test
  fun assertWriteIntentReadAccess(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        ThreadingAssertions.assertWriteIntentReadAccess()
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Access is allowed from write thread only")
  }

  @Test
  fun assertNoOwnReadAccessRead(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        readAction {
          ThreadingAssertions.assertNoOwnReadAccess()
        }
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Must not execute inside read action")
  }

  @Test
  fun assertNoOwnReadAccessWrite(): Unit = timeoutRunBlocking {
    val err = withContext(Dispatchers.Default) {
      runCatching {
        writeAction {
          ThreadingAssertions.assertNoReadAccess()
        }
      }.exceptionOrNull()
    }
    assertThat(err).isNotNull.hasMessageContaining("Must not execute inside read action")
  }

  @Test
  fun suspendFunctionExecutesFinallyWhenInjectedCheckThrowsOnResumption(): Unit = timeoutRunBlocking {
    val call = startAnnotatedSuspendCall()
    val continuation = call.suspendedContinuation.await()
    // Resume on the EDT. The method re-entry runs the injected check again, and the check fails.
    // The processor throws like DefaultLogger.error(), because the test logger only records the error.
    withContext(Dispatchers.EDT) {
      LoggedErrorProcessor.executeWith<Throwable>(object : LoggedErrorProcessor() {
        override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> {
          throw AssertionError(message, t)
        }
      }) {
        continuation.resume(Unit)
      }
    }
    // The correct behavior: the finally block runs and the call completes.
    assertThat(call.finallyExecuted).isTrue()
    assertThat(call.completion.await().getOrThrow()).isTrue()
  }

  @Test
  fun suspendFunctionExecutesFinallyOnBackgroundThreadResumption(): Unit = timeoutRunBlocking {
    val call = startAnnotatedSuspendCall()
    val continuation = call.suspendedContinuation.await()
    // Resume on a background thread. The injected check passes at the re-entry.
    withContext(Dispatchers.Default) {
      continuation.resume(Unit)
    }
    assertThat(call.finallyExecuted).isTrue()
    assertThat(call.completion.await().getOrThrow()).isTrue()
  }

  private class AnnotatedSuspendCall {
    var finallyExecuted: Boolean = false
    val suspendedContinuation: CompletableDeferred<Continuation<Unit>> = CompletableDeferred()
    val completion: CompletableDeferred<Result<Boolean>> = CompletableDeferred()
  }

  private suspend fun startAnnotatedSuspendCall(): AnnotatedSuspendCall {
    val call = AnnotatedSuspendCall()
    // Start on a background thread. The injected check passes at the first entry.
    // The completion continuation has no interceptor, so the resumption runs on the resuming thread.
    withContext(Dispatchers.Default) {
      suspend {
        KotlinSuspendThreadingAnnotationChecks.backgroundThreadWithFinally(
          onSuspend = { continuation -> call.suspendedContinuation.complete(continuation) },
          onFinally = { call.finallyExecuted = true },
        )
      }.startCoroutine(Continuation(EmptyCoroutineContext) { result -> call.completion.complete(result) })
    }
    return call
  }

}

internal enum class ThreadingAnnotation(val javaCall: () -> Boolean, val kotlinCall: () -> Boolean, val message: String) {
  EDT(JavaThreadingAnnotationChecks::edt, KotlinThreadingAnnotationChecks::edt,
      "Access is allowed from Event Dispatch Thread (EDT) only"),
  BACKGROUND_THREAD(JavaThreadingAnnotationChecks::backgroundThread, KotlinThreadingAnnotationChecks::backgroundThread,
                    "Access from Event Dispatch Thread (EDT) is not allowed"),
  READ_LOCK(JavaThreadingAnnotationChecks::readLock, KotlinThreadingAnnotationChecks::readLock,
            "Read access is allowed from inside read-action only"),
  WRITE_LOCK(JavaThreadingAnnotationChecks::writeLock, KotlinThreadingAnnotationChecks::writeLock,
             "Write access is allowed inside write-action only"),
  READ_LOCK_ABSENCE(JavaThreadingAnnotationChecks::readLockAbsence, KotlinThreadingAnnotationChecks::readLockAbsence,
                    "Must not execute inside read action"),
}

private object KotlinThreadingAnnotationChecks {
  @RequiresEdt
  fun edt(): Boolean = true

  @RequiresBackgroundThread
  fun backgroundThread(): Boolean = true

  @RequiresReadLock
  fun readLock(): Boolean = true

  @RequiresWriteLock
  fun writeLock(): Boolean = true

  @RequiresReadLockAbsence
  fun readLockAbsence(): Boolean = true

  // These two annotation together don't make sense
  // But it is useful to test that both of them are instrumented
  @RequiresEdt
  @RequiresBackgroundThread
  fun severalAnnotations(): Boolean = true
}

private object KotlinSuspendThreadingAnnotationChecks {
  /**
   * The build does not inject an assertion into a suspend function.
   * A resumption of the continuation re-enters the method, so an injected check would run again on the resuming thread.
   */
  @RequiresBackgroundThread
  suspend fun backgroundThreadWithFinally(onSuspend: (Continuation<Unit>) -> Unit, onFinally: () -> Unit): Boolean {
    try {
      // The test resumes the raw continuation itself, so cancellation support is not needed.
      @Suppress("SuspendCoroutineLacksCancellationGuarantees")
      suspendCoroutine { onSuspend(it) }
      return true
    }
    finally {
      onFinally()
    }
  }
}
