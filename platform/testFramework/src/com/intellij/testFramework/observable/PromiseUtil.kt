// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.observable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil.waitForPromise
import org.jetbrains.concurrency.Promise
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
