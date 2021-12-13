// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress

import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.util.concurrency.Semaphore
import com.intellij.util.getValue
import com.intellij.util.setValue
import junit.framework.TestCase.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KClass

private const val TIMEOUT_MS: Long = 1000

fun submitTasks(service: ExecutorService, task: () -> Unit) {
  service.execute(task)
  service.submit(task)
}

fun submitTasksBlocking(service: ExecutorService, task: () -> Unit) {
  val callable = Callable(task)
  val callables = listOf(callable, callable)
  service.invokeAny(callables) // one of callables may not be executed
  service.invokeAll(callables)
}

fun neverEndingStory(): Nothing {
  while (true) {
    ProgressManager.checkCanceled()
    Thread.sleep(1)
  }
}

fun withRootJob(action: (rootJob: Job) -> Unit): Job {
  return CoroutineScope(Dispatchers.Default).async {
    withJob {
      action(coroutineContext.job)
    }
  }
}

fun Semaphore.waitUp(): Unit = assertTrue(waitFor(TIMEOUT_MS))

fun <X> Future<X>.waitGet(): X = get(TIMEOUT_MS, TimeUnit.MILLISECONDS)

fun waitAssertCompletedNormally(future: Future<*>) {
  future.waitGet()
  assertTrue(future.isDone)
  assertFalse(future.isCancelled)
}

fun waitAssertCompletedWith(future: Future<*>, clazz: KClass<out Throwable>) {
  try {
    future.waitGet()
    fail("ExecutionException expected")
  }
  catch (e: ExecutionException) {
    assertInstanceOf(e.cause, clazz.java)
  }
}

fun waitAssertCompletedWithCancellation(future: Future<*>) {
  waitAssertCompletedWith(future, CancellationException::class)
}

fun Job.waitJoin(): Unit = runBlocking {
  withTimeout(TIMEOUT_MS) {
    join()
  }
}

fun waitAssertCompletedNormally(job: Job) {
  job.waitJoin()
  assertFalse(job.isCancelled)
}

fun waitAssertCancelled(job: Job) {
  job.waitJoin()
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
      gotIt.waitUp()
    }
  }
  finally {
    Thread.setDefaultUncaughtExceptionHandler(savedHandler)
  }
  return throwable
}
