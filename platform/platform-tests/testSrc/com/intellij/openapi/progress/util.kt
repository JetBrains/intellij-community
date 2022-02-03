// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.concurrency.resetThreadContext
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.*
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlinx.coroutines.sync.Semaphore as KSemaphore

const val TEST_TIMEOUT_MS: Long = 1000

fun timeoutRunBlocking(action: suspend CoroutineScope.() -> Unit) {
  runBlocking {
    withTimeout(TEST_TIMEOUT_MS, action)
  }
}

fun neverEndingStory(): Nothing {
  while (true) {
    Cancellation.checkCancelled()
    Thread.sleep(1)
  }
}

fun timeoutRunBlockingWithContext(action: suspend CoroutineScope.() -> Unit) {
  timeoutRunBlocking {
    resetThreadContext().use {
      action()
    }
  }
}

fun withRootJob(action: (rootJob: Job) -> Unit): Job {
  return CoroutineScope(Dispatchers.Default).async {
    withJob(action)
  }
}

fun Semaphore.timeoutWaitUp() {
  assertTrue(waitFor(TEST_TIMEOUT_MS))
}

suspend fun KSemaphore.timeoutAcquire() {
  withTimeout(TEST_TIMEOUT_MS) {
    acquire()
  }
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
  waitAssertCompletedWith(future, CancellationException::class)
}

fun Job.timeoutJoinBlocking(): Unit = runBlocking {
  timeoutJoin()
}

suspend fun Job.timeoutJoin() {
  withTimeout(TEST_TIMEOUT_MS) {
    join()
  }
}

suspend fun <T> Deferred<T>.timeoutAwait(): T {
  return withTimeout(TEST_TIMEOUT_MS) {
    await()
  }
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
  val children = parent.children.toSet()
  job.ensureActive()
  assertTrue(job in children)
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
      override fun processError(category: String, message: String?, t: Throwable, details: Array<out String>): Boolean {
        throwable = t
        gotIt.up()
        return false
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
        catch (e: JobCanceledException) {
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
