// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.TestLoggerFactory.TestLoggerAssertionError
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

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
    test()
  }
}

fun indicatorTest(test: (ProgressIndicator) -> Unit) {
  val indicator = EmptyProgressIndicator()
  withIndicator(indicator) {
    test(indicator)
  }
  assertFalse(indicator.isCanceled)
}

fun withIndicator(indicator: ProgressIndicator, action: () -> Unit) {
  ProgressManager.getInstance().runProcess(action, indicator)
}

inline fun <reified T : Any> assertInstanceOf(instance: Any?): T {
  return assertInstanceOf(T::class.java, instance)
}

inline fun <reified T : Throwable> assertLogThrows(executable: () -> Unit): T {
  return assertThrows<T> {
    val loggerError = assertThrows<TestLoggerAssertionError>(executable)
    throw requireNotNull(loggerError.cause)
  }
}
