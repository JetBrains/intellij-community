// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase.*
import kotlinx.coroutines.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

private const val TIMEOUT_MS: Long = 1000

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
  if (current !in parent.children) {
    @Suppress("EXPERIMENTAL_API_USAGE_ERROR")
    throw current.getCancellationException()
  }
  return current
}
