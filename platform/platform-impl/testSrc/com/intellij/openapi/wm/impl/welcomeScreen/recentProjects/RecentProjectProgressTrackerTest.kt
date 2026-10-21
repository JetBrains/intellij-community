// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskHandle
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.Path

@TestApplication
class RecentProjectProgressTrackerTest {
  private val projectFixture = projectFixture()
  private val project: Project get() = projectFixture.get()

  private val taskStorage get() = TaskStorage.getInstance()

  @AfterEach
  fun removeTasks() {
    taskStorage.removeTasks { true }
  }

  @Test
  fun `a followed project reports the tasks it already runs`() = timeoutRunBlocking {
    addTask("Fetching")

    withTracker { tracker ->
      tracker.follow(projectPath(), project)

      val running = tracker.awaitProgressOf(count = 1)
      assertThat(running.map { it.title }).containsExactly("Fetching")
      assertThat(running.single().isCancellable).isTrue()
    }
  }

  @Test
  fun `the task the user most likely asked for comes first`() = timeoutRunBlocking {
    addTask("Indexing", isVisibleInStatusBar = false, isCancellable = true)
    addTask("Scanning", isVisibleInStatusBar = true, isCancellable = false)
    addTask("Updating", isVisibleInStatusBar = true, isCancellable = true)
    addTask("Fetching", isVisibleInStatusBar = true, isCancellable = true)

    withTracker { tracker ->
      tracker.follow(projectPath(), project)

      // Seen before unseen, cancellable before not, and the one that started first before the one that followed it.
      val running = tracker.awaitProgressOf(count = 4)
      assertThat(running.map { it.title }).containsExactly("Updating", "Fetching", "Scanning", "Indexing")
    }
  }

  @Test
  fun `a project reports nothing once its last task ends`() = timeoutRunBlocking {
    val task = addTask("Fetching")

    withTracker { tracker ->
      tracker.follow(projectPath(), project)
      tracker.awaitProgressOf(count = 1)

      taskStorage.removeTask(task)

      assertThat(tracker.awaitProgressOf(count = 0)).isEmpty()
    }
  }

  @Test
  fun `cancelling by id cancels the task it names`() = timeoutRunBlocking {
    val task = addTask("Fetching")

    withTracker { tracker ->
      tracker.follow(projectPath(), project)

      tracker.cancel(tracker.awaitProgressOf(count = 1).single().id)

      assertThat(task.status.first { it is TaskStatus.Canceled }).isInstanceOf(TaskStatus.Canceled::class.java)
    }
  }

  @Test
  fun `a project is empty when it runs nothing, and when it has no path`() {
    val progress = RecentProjectProgress(ProgressId.Clone(Path("/a")), "Cloning", null, null, false, "Cancel")
    val byProject = ProjectsProgress(mapOf(Path("/a") to listOf(progress)))

    assertThat(byProject.getProgressOf(itemOf(Path("/a")))).containsExactly(progress)
    assertThat(byProject.getProgressOf(itemOf(Path("/b")))).isEmpty()
    assertThat(byProject.getProgressOf(itemOf(path = null))).isEmpty()
  }

  // The tracker collects until its scope is cancelled, so it gets one of its own that ends with the test.
  private suspend fun CoroutineScope.withTracker(body: suspend (RecentProjectProgressTracker) -> Unit) {
    val scope = childScope("RecentProjectProgressTrackerTest")
    try {
      body(RecentProjectProgressTracker(scope))
    }
    finally {
      scope.cancel()
    }
  }

  private fun addTask(
    title: String,
    isVisibleInStatusBar: Boolean = true,
    isCancellable: Boolean = true,
  ): TaskHandle = taskStorage.addTask(
    project = project,
    title = title,
    cancellation = if (isCancellable) TaskCancellation.cancellable() else TaskCancellation.nonCancellable(),
    suspendable = TaskSuspension.NonSuspendable,
    visibleInStatusBar = isVisibleInStatusBar,
  )

  // The tracker publishes from its own coroutine, so a reader waits for the report it expects.
  private suspend fun RecentProjectProgressTracker.awaitProgressOf(count: Int): List<RecentProjectProgress> {
    val item = itemOf(projectPath())
    return projectsProgress.first { it.getProgressOf(item).size == count }.getProgressOf(item)
  }

  private fun projectPath(): Path = Path(project.basePath!!)

  private fun itemOf(path: Path?): RecentProjectItem = RecentProjectItem(
    projectPath = path?.toString() ?: "",
    projectName = "project",
    displayName = "project",
    projectGroup = null,
    activationTimestamp = null,
  )
}
