// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.concurrency.currentThreadContext
import com.intellij.concurrency.installThreadContext
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories

/**
 * A project close must finish even when its caller started it with a thread context whose job dies with the
 * project scope, which is what an action performed in the project frame installs.
 */
@TestApplication
internal class ProjectClosingResilienceTest {
  private val firstDir by tempPathFixture()

  @Test
  fun `a close started under a job of the project scope still removes and disposes the project`() {
    val openedProject = openProject(firstDir)
    // The write action that removes and disposes the project throws while it acquires a contended lock when the
    // thread context carries a cancelled job. The lock contention is timing, so the test reads the cause instead:
    // the job the close runs under once the project scope is cancelled.
    val closedUnderCancelledJob = AtomicReference<Boolean?>(null)
    // The project bus is gone by the time `projectClosed` fires, so the subscription lives on the application bus.
    val subscription = Disposer.newDisposable("ProjectClosingResilienceTest")
    ApplicationManager.getApplication().messageBus.connect(subscription).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        if (project === openedProject) {
          closedUnderCancelledJob.set(currentThreadContext()[Job]?.isCancelled == true)
        }
      }
    })
    try {
      val callerJob = Job(parent = (openedProject as ComponentManagerEx).getCoroutineScope().coroutineContext.job)
      val closed = invokeAndWaitIfNeeded {
        installThreadContext(callerJob, replace = true) {
          ProjectManagerEx.getInstanceEx().forceCloseProject(openedProject, save = false)
        }
      }

      assertThat(closed).describedAs("closeProject result").isTrue()
      assertThat(closedUnderCancelledJob.get()).describedAs("projectClosed ran under a cancelled job").isFalse()
      assertThat(openedProject.isDisposed).describedAs("the project is disposed").isTrue()
      assertThat(ProjectManagerEx.getInstanceEx().openProjects).describedAs("open projects").doesNotContain(openedProject)
    }
    finally {
      Disposer.dispose(subscription)
      PlatformTestUtil.forceCloseProjectWithoutSaving(openedProject)
    }
  }

  private fun openProject(dir: Path): Project {
    dir.createDirectories()
    return checkNotNull(ProjectManagerEx.getInstanceEx().openProject(dir, OpenProjectTask {})) { "cannot open $dir" }
  }
}
