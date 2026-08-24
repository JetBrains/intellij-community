// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("NavigationTestUtil")
package com.intellij.testFramework

import com.intellij.diagnostic.dumpCoroutines
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.EDT
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val DEFAULT_AWAIT_TIMEOUT: Duration = 2.minutes

/**
 * Loops until completion, so continuations submitted
 * while waiting (e.g., post-navigation caret adjustments) are awaited as well.
 */
@TestOnly
@RequiresEdt
@JvmOverloads
fun awaitPendingNavigation(project: Project, timeoutMillis: Long = DEFAULT_AWAIT_TIMEOUT.inWholeMilliseconds) {
  EDT.assertIsEdt()
  check(!ApplicationManager.getApplication().isWriteAccessAllowed) {
    "awaitPendingNavigation() must be called outside a write action"
  }
  while (true) {
    if (project.isDisposed) {
      return
    }
    val barrier = NavigationService.pendingNavigation(project)
    if (barrier.isCompleted) break
    try {
      PlatformTestUtil.waitForFuture(barrier.asCompletableFuture(), timeoutMillis)
    }
    catch (e: Throwable) {
      throw AssertionError(
        "Timed out after $timeoutMillis ms awaiting pending navigation in project $project",
        e.cause
      )
    }
  }
}

@TestOnly
suspend fun Project.awaitPendingNavigation(timeout: Duration = DEFAULT_AWAIT_TIMEOUT) {
  try {
    val project = this
    withTimeout(timeout) {
      while (true) {
        if (project.isDisposed) break
        val barrier = NavigationService.pendingNavigation(project)
        if (barrier.isCompleted) break
        barrier.join()
      }
    }
  }
  catch (e: TimeoutCancellationException) {
    throw AssertionError(
      "Timed out after $timeout ms awaiting pending navigation in project $this.\nCoroutine dump:\n${dumpCoroutines()}",
      e.cause
    )
  }
}

/**
 * Awaits pending navigation unless the call happens under a write action or the project is disposed.
 * To be used by test fixtures after editor/named actions that may submit navigation as a side effect.
 *
 * A test which needs to observe navigation in flight awaits it explicitly with [awaitPendingNavigation] instead
 * of going through a fixture.
 */
@TestOnly
@RequiresEdt
fun awaitPendingNavigationIfEnabled(project: Project) {
  val application = ApplicationManager.getApplication()
  if (application.isWriteAccessAllowed || project.isDisposed) {
    return
  }
  awaitPendingNavigation(project)
}
