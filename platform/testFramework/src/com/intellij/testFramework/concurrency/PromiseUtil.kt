// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.concurrency

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.concurrency.waitForPromise
import com.intellij.testFramework.PlatformTestUtil.waitForPromise
import org.jetbrains.concurrency.Promise
import kotlin.time.Duration

/**
 * Waits with timeout for completion of [this] promise WITH blocking a thread.
 * BUT: It pumps EDT events if function calls on it.
 * @see com.intellij.openapi.concurrency.waitForPromise
 */
fun <R> Promise<R>.waitForPromiseAndPumpEdt(timeout: Duration): R? {
  val application = ApplicationManager.getApplication()
  val result = when (application.isDispatchThread) {
    true -> waitForPromise(this, timeout.inWholeMilliseconds)
    else -> waitForPromise(timeout)
  }
  return result
}
