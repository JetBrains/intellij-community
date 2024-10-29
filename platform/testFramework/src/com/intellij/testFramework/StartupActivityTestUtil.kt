// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.minutes

object StartupActivityTestUtil {

  /**
   * This method is a shortcut. If you decide to use it, please plan a task to remove it.
   * You should not wait for activities - they may never finish. Instead, you should wait for
   * side effects (events) produced by that activity (import finish, project refresh, etc.)
   *
   * This method is an okay-ish short-term solution, when you need more time to prepare proper events that you can wait for in tests.
   */
  @Deprecated("Please wait for proper event instead. Activities might never finish by design.")
  @JvmStatic
  fun waitForProjectActivitiesToComplete(project: Project) {
    val runningActivities = (StartupManager.getInstance(project) as StartupManagerImpl).getRunningProjectActivities()

    if (ApplicationManager.getApplication().isDispatchThread) {
      for (job in runningActivities.values) {
        PlatformTestUtil.waitForFuture(job.asCompletableFuture())
      }
    }
    else {
      runBlockingMaybeCancellable {
        try {
          withTimeout(2.minutes) {
            runningActivities.values.joinAll()
          }
        }
        catch (e: TimeoutCancellationException) {
          throw RuntimeException("Cannot wait for project activities to complete\n${dumpCoroutines()}", e)
        }
      }
    }
  }
}