// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.util.application
import kotlinx.coroutines.*
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.jupiter.api.fail
import kotlin.time.Duration
import kotlin.time.DurationUnit.SECONDS
import kotlin.time.toDuration

object DumbModeTestUtils {
  /**
   * "Eternal" means that test framework will not terminate the task. Please stop dumb mode in the end of test. Use wisely.
   *
   * Always invoke [Job.cancel] or [endEternalDumbModeTaskAndWaitForSmartMode] in test's `tearDown` in `finally` block.
   */
  @JvmStatic
  fun startEternalDumbModeTask(project: Project): Job {
    var dumbModeJob: Job? = null
    @Suppress("RAW_RUN_BLOCKING")
    runBlocking {
      val dumbModeStarted = CompletableDeferred<Boolean>()
      withTimeout(10.toDuration(SECONDS)) {
        dumbModeJob = CoroutineScope(Dispatchers.Main.immediate + Job()).launch {
          DumbServiceImpl.getInstance(project).runInDumbMode {
            dumbModeStarted.complete(true)
            delay(Duration.INFINITE)
          }
        }
        dumbModeStarted.await()
      }
    }
    assertTrue("Dumb mode didn't start", DumbService.isDumb(project))
    return dumbModeJob ?: fail("Could not start dumb mode task")
  }

  @JvmStatic
  fun endEternalDumbModeTaskAndWaitForSmartMode(project: Project, job: Job) {
    job.cancel()
    waitForSmartMode(project)
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