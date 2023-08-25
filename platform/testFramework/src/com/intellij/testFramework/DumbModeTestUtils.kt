// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.runWithModalProgressBlocking
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.util.ThrowableRunnable
import com.intellij.util.application
import kotlinx.coroutines.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration

object DumbModeTestUtils {
  class EternalTaskShutdownToken internal constructor(private val dumbTask: Job) : AutoCloseable {
    override fun close() {
      dumbTask.cancel(null)
    }
  }

  /**
   * "Eternal" means that test framework will not terminate the task. Please stop dumb mode in the end of test. Use wisely.
   *
   * Always invoke [EternalTaskShutdownToken.close] or [endEternalDumbModeTaskAndWaitForSmartMode] in test's `tearDown` in `finally` block.
   */
  @JvmStatic
  fun startEternalDumbModeTask(project: Project): EternalTaskShutdownToken {
    val finishDumbTask = CompletableDeferred<Boolean>()
    runModalIfEdt(project) {
      val dumbTaskStarted = CompletableDeferred<Boolean>()
      withTimeout(10.toDuration(SECONDS)) {
        DumbServiceImpl.getInstance(project).runInDumbMode {
          CoroutineScope(Job()).launch {
            // The trick is that we don't need EDT, neither a write action to start dumb task, if we already in dumb mode.
            DumbServiceImpl.getInstance(project).runInDumbMode {
              dumbTaskStarted.complete(true)
              finishDumbTask.await()
            }
          }
          // However, we need to make sure that dumb mode does not finish before the second task is started.
          // Otherwise, the second task will need write action and EDT (to start a new dumb mode). There will be a deadlock,
          // if startEternalDumbModeTask is invoked from EDT (because CoroutineScope(Job()).launch started with NON_MODAL modality).
          dumbTaskStarted.await()
        }
      }
    }
    assertTrue("Dumb mode didn't start", DumbService.isDumb(project))
    return EternalTaskShutdownToken(finishDumbTask)
  }

  private fun runModalIfEdt(project: Project, action: suspend CoroutineScope.() -> Unit) {
    if (application.isDispatchThread) {
      runWithModalProgressBlocking(project, "test", action)
    }
    else {
      runBlockingMaybeCancellable(action)
    }
  }

  @JvmStatic
  fun endEternalDumbModeTask(task: EternalTaskShutdownToken) {
    task.close()
  }

  @JvmStatic
  fun endEternalDumbModeTaskAndWaitForSmartMode(project: Project, task: EternalTaskShutdownToken) {
    endEternalDumbModeTask(task)
    waitForSmartMode(project)
  }

  /**
   * This method simulates the situation that someone started dumb task, and then runnable is executed while dumb mode is active.
   * This artificial dumb task will finish immediately after runnable has finished
   *
   * This method can be invoked from any thread. It will switch to EDT to start/stop dumb mode. Runnable itself will be invoked from
   * method's invocation thread.
   */
  @JvmStatic
  fun runInDumbModeSynchronously(project: Project, runnable: ThrowableRunnable<in Throwable>) {
    computeInDumbModeSynchronously(project) {
      runnable.run()
    }
  }

  /**
   * This method simulates the situation that someone started dumb task, and then runnable is executed while dumb mode is active.
   * This artificial dumb task will finish immediately after runnable has finished
   *
   * This method can be invoked from any thread. It will switch to EDT to start/stop dumb mode. Runnable itself will be invoked from
   * method's invocation thread.
   */
  @JvmStatic
  fun <T> computeInDumbModeSynchronously(project: Project, computable: ThrowableComputable<T, in Throwable>): T {
    val token = startEternalDumbModeTask(project)
    try {
      return computable.compute()
    }
    finally {
      endEternalDumbModeTaskAndWaitForSmartMode(project, token)
    }
  }

  /**
   * Waits for smart mode at most 10 seconds and throws AssertionError if smart mode didn't start.
   *
   * Can be invoked from any thread (even from EDT).
   */
  @JvmStatic
  fun waitForSmartMode(project: Project) {
    if (application.isDispatchThread) {
      PlatformTestUtil.waitWithEventsDispatching("Dumb mode didn't finish", { !DumbService.isDumb(project) }, 10)
    }
    else {
      DumbServiceImpl.getInstance(project).waitForSmartMode(10_000)
    }
    assertFalse("Dumb mode didn't finish", DumbService.isDumb(project))
  }
}