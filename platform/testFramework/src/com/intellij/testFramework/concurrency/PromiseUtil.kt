// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.testFramework.PlatformTestUtil.waitForPromise
import kotlinx.coroutines.withTimeout
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.await
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun <R> Promise<R>.waitForPromise(
  timeout: Duration = 1.minutes
): R {
  val application = ApplicationManager.getApplication()
  val result = when (application.isDispatchThread) {
    true -> waitForPromise(this, timeout.inWholeMilliseconds)
    else -> blockingGet(timeout.inWholeSeconds.toInt(), TimeUnit.SECONDS)
  }
  @Suppress("UNCHECKED_CAST")
  return result as R
}

fun <R> Promise<*>.waitForPromise(
  timeout: Duration = 1.minutes,
  action: ThrowableComputable<R, Throwable>
): R {
  val result = action.compute()
  waitForPromise(timeout)
  return result
}

suspend fun <R> Promise<R>.awaitPromise(
  timeout: Duration = 1.minutes
): R {
  return withTimeout(timeout) {
    await()
  }
}

suspend fun <R> Promise<*>.awaitPromise(
  timeout: Duration = 1.minutes,
  action: suspend () -> R
): R {
  val result = action()
  awaitPromise(timeout)
  return result
}
