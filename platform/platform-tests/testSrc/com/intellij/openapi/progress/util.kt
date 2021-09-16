// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress

import com.intellij.testFramework.UsefulTestCase.assertInstanceOf
import com.intellij.util.concurrency.Semaphore
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import kotlinx.coroutines.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private const val TIMEOUT_MS: Long = 1000

fun neverEndingStory(): Nothing {
  while (true) {
    ProgressManager.checkCanceled()
    Thread.sleep(1)
  }
}

fun Semaphore.waitUp(): Unit = assertTrue(waitFor(TIMEOUT_MS))

fun <X> Future<X>.waitGet(): X = get(TIMEOUT_MS, TimeUnit.MILLISECONDS)

fun waitAssertCompletedWithCancellation(future: Future<*>) {
  try {
    future.waitGet()
    fail("ExecutionException expected")
  }
  catch (e: ExecutionException) {
    assertInstanceOf(e.cause, CancellationException::class.java)
  }
}
