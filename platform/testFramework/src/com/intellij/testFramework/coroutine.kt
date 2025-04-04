// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.progress.withCurrentThreadCoroutineScopeBlocking
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.testFramework.common.DEFAULT_TEST_TIMEOUT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.yield
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

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

@RequiresEdt
fun executeSomeCoroutineTasksAndDispatchAllInvocationEvents() {
  repeat(3) {
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "") {
      yield()
    }
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
  }
}

@Deprecated("use com.intellij.testFramework.common.waitUnti")
@TestOnly
suspend fun waitUntil(message: String? = null, timeout: Duration = DEFAULT_TEST_TIMEOUT, condition: suspend CoroutineScope.() -> Boolean) {
  return com.intellij.testFramework.common.waitUntil(message, timeout, condition)
}

@Deprecated("The method is supposed to be called from Java only as a test-in-coroutine launcher")
@TestOnly
fun runTestInCoroutineScope(block: ThrowableRunnable<Throwable>, timeout: java.time.Duration) {
  timeoutRunBlocking(timeout = timeout.toKotlinDuration()) {
    val job = withCurrentThreadCoroutineScopeBlocking {
      block.run()
    }.second
    job.join()
  }
}
