// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration

@RequiresEdt
fun executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project: Project) {
  repeat(3) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    runWithModalProgressBlocking(project, "") {
      yield()
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}

@TestOnly
suspend fun waitUntil(message: String? = null, timeout: Duration = DEFAULT_TEST_TIMEOUT, condition: suspend CoroutineScope.() -> Boolean) {
  try {
    withTimeout(timeout) {
      while (!condition()) {
        delay(50)
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    println(dumpCoroutines())
    if (message != null) {
      throw AssertionError(message, e)
    }
    else {
      throw AssertionError(e)
    }
  }
}