// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlinx.coroutines.sync.Semaphore as KSemaphore

private const val TEST_TIMEOUT_MS: Long = 1000

fun neverEndingStory(): Nothing {
  while (true) {
    Cancellation.checkCancelled()
    Thread.sleep(1)
  }
}

fun withRootJob(action: (rootJob: Job) -> Unit): Job {
  @OptIn(DelicateCoroutinesApi::class)
  return GlobalScope.async {
    blockingContextScope {
      val currentJob = requireNotNull(Cancellation.currentJob())
      action(currentJob)
    }
  }
}

fun Semaphore.timeoutWaitUp() {
  assertTrue(waitFor(TEST_TIMEOUT_MS))
}

fun <X> Future<X>.timeoutGet(): X {
  return get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
}

fun waitAssertCompletedNormally(future: Future<*>) {
  future.timeoutGet()
  assertTrue(future.isDone)
  assertFalse(future.isCancelled)
}

fun waitAssertCompletedWith(future: Future<*>, clazz: KClass<out Throwable>) {
  val ee = assertThrows<ExecutionException> {
    future.timeoutGet()
  }
  assertInstanceOf(clazz.java, ee.cause)
}

fun waitAssertCompletedWithCancellation(future: Future<*>) {
  assertThrows<CancellationException> {
    future.get()
  }
}

fun Job.timeoutJoinBlocking(): Unit = timeoutRunBlocking {
  join()
}

fun waitAssertCompletedNormally(job: Job) {
  job.timeoutJoinBlocking()
  assertFalse(job.isCancelled)
}

fun waitAssertCancelled(job: Job) {
  job.timeoutJoinBlocking()
  assertTrue(job.isCancelled)
}

fun assertCurrentJobIsChildOf(parent: Job): Job {
  val current = requireNotNull(Cancellation.currentJob())
  assertJobIsChildOf(current, parent)
  return current
}

fun assertJobIsChildOf(job: Job, parent: Job) {
  @OptIn(ExperimentalCoroutinesApi::class)
  assertSame(parent, job.parent)
}

fun loggedError(canThrow: Semaphore): Throwable {
  var throwable by AtomicReference<Throwable>()
  val gotIt = Semaphore(2)
  val savedHandler = Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler { _, _ ->
    gotIt.up()
  }
  try {
    LoggedErrorProcessor.executeWith<Nothing>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<out String>, t: Throwable?): Set<Action> {
        throwable = t!!
        gotIt.up()
        return Action.NONE
      }
    }) {
      canThrow.up()
      gotIt.timeoutWaitUp()
    }
  }
  finally {
    Thread.setDefaultUncaughtExceptionHandler(savedHandler)
  }
  return throwable
}

fun blockingContextTest(test: () -> Unit) {
  timeoutRunBlocking {
    blockingContext(test)
  }
}

fun indicatorTest(test: (ProgressIndicator) -> Unit) {
  val indicator = EmptyProgressIndicator()
  withIndicator(indicator) {
    test(indicator)
  }
  assertFalse(indicator.isCanceled)
}

internal fun withIndicator(indicator: ProgressIndicator, action: () -> Unit) {
  ProgressManager.getInstance().runProcess(action, indicator)
}

internal suspend fun <X> childCallable(cs: CoroutineScope, action: () -> X): Callable<X> {
  val lock = KSemaphore(permits = 1, acquiredPermits = 1)
  var callable by AtomicReference<Callable<X>>()
  cs.launch(Dispatchers.IO) {
    suspendCancellableCoroutine { continuation: Continuation<Unit> ->
      callable = Callable<X> {
        try {
          action().also {
            continuation.resume(Unit)
          }
        }
        catch (e: CeProcessCanceledException) {
          continuation.resume(Unit)
          throw e
        }
        catch (e: Throwable) {
          continuation.resumeWithException(e) // fail parent scope
          throw e
        }
      }
      lock.release()
    }
  }
  lock.acquire()
  return callable
}

inline fun <reified T> assertInstanceOf(instance: Any?): T {
  return assertInstanceOf(T::class.java, instance)
}

inline fun <reified T : Throwable> assertLogThrows(executable: () -> Unit): T {
  return assertThrows<T> {
    val loggerError = assertThrows<TestLoggerAssertionError>(executable)
    throw requireNotNull(loggerError.cause)
  }
}
