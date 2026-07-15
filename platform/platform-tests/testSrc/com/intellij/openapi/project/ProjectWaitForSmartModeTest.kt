// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.runInDumbMode
import com.intellij.openapi.project.waitForSmartMode
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.rules.ProjectModelExtension
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class ProjectWaitForSmartModeTest {
  @RegisterExtension
  private val projectModel = ProjectModelExtension()

  private val project get() = projectModel.project

  @Test
  fun `test waitForDumbMode canceled when project closed`(): Unit = timeoutRunBlocking(10.seconds) {
    val dumbModeStartedLatch = CompletableDeferred<Unit>()
    val canExitDumbModeLatch = CompletableDeferred<Unit>()
    val dumbModeJob = launch {
      DumbService.getInstance(project).runInDumbMode {
        dumbModeStartedLatch.complete(Unit)
        canExitDumbModeLatch.await()
      }
    }
    dumbModeStartedLatch.await()
    Assertions.assertThat(DumbService.isDumb(project)).isTrue()

    val awaitingJobStartedLatch = CompletableDeferred<Unit>()
    val awaitingJob = launch {
      awaitingJobStartedLatch.complete(Unit)
      project.waitForSmartMode()
    }
    awaitingJobStartedLatch.await()


    project.closeProjectAsync()
    Disposer.dispose(project)

    waitUntil(timeout = 100.milliseconds) { awaitingJob.isCancelled }

    dumbModeJob.cancelAndJoin()
    awaitingJob.cancelAndJoin()
  }

  @Test
  fun `test waitForDumbMode successfully resumes`(): Unit = timeoutRunBlocking(10.seconds) {
    val dumbModeStartedLatch = CompletableDeferred<Unit>()
    val canExitDumbModeLatch = CompletableDeferred<Unit>()
    val dumbModeJob = launch {
      DumbService.getInstance(project).runInDumbMode {
        dumbModeStartedLatch.complete(Unit)
        canExitDumbModeLatch.await()
      }
    }
    dumbModeStartedLatch.await()
    Assertions.assertThat(DumbService.isDumb(project)).isTrue()

    val awaitingJobStartedLatch = CompletableDeferred<Unit>()
    val awaitingJob = launch {
      awaitingJobStartedLatch.complete(Unit)
      project.waitForSmartMode()
    }
    awaitingJobStartedLatch.await()


    canExitDumbModeLatch.complete(Unit)
    dumbModeJob.join()
    awaitingJob.join()
  }
}
