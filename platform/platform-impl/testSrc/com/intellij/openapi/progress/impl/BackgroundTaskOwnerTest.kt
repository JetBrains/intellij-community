// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.project.ProjectManager
import com.intellij.platform.ide.progress.BackgroundTaskOwner
import com.intellij.platform.ide.progress.BackgroundTaskOwnerKind
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import fleet.util.UID
import kotlinx.coroutines.CoroutineScope
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
@Suppress("DEPRECATION")
internal class BackgroundTaskOwnerTest {

  private val project by projectFixture()

  @Test
  fun `a project owner binds the task to the project`(): Unit = timeoutRunBlocking {
    val task = runAndCaptureTask(BackgroundTaskOwner.project(project))

    assertEquals(BackgroundTaskOwnerKind.PROJECT, task.ownerKind)
    assertEquals(project.projectId(), task.projectId)
  }

  @Test
  fun `the project overload binds the task to the project`(): Unit = timeoutRunBlocking {
    val task = runAndCaptureTask { title, action -> withBackgroundProgress(project, title, action) }

    assertEquals(BackgroundTaskOwnerKind.PROJECT, task.ownerKind)
    assertEquals(project.projectId(), task.projectId)
  }

  @Test
  fun `a default project owner stores no project id`(): Unit = timeoutRunBlocking {
    val task = runAndCaptureTask(BackgroundTaskOwner.project(ProjectManager.getInstance().defaultProject))

    assertEquals(BackgroundTaskOwnerKind.PROJECT, task.ownerKind)
    assertNull(task.projectId)
  }

  @Test
  fun `a last focused frame owner stores no project id`(): Unit = timeoutRunBlocking {
    val task = runAndCaptureTask(BackgroundTaskOwner.guess())

    assertEquals(BackgroundTaskOwnerKind.GUESS, task.ownerKind)
    assertNull(task.projectId)
  }

  @Test
  fun `an all frames owner stores no project id`(): Unit = timeoutRunBlocking {
    val task = runAndCaptureTask(BackgroundTaskOwner.allFrames())

    assertEquals(BackgroundTaskOwnerKind.ALL_FRAMES, task.ownerKind)
    assertNull(task.projectId)
  }

  @Test
  fun `the application collector shows the tasks of the default project and of the last focused frame`() {
    assertTrue(isTaskShownByCollector(BackgroundTaskOwnerKind.PROJECT, taskProjectId = null, collectorProjectId = null))
    assertTrue(isTaskShownByCollector(BackgroundTaskOwnerKind.GUESS, taskProjectId = null, collectorProjectId = null))
    assertFalse(isTaskShownByCollector(BackgroundTaskOwnerKind.ALL_FRAMES, taskProjectId = null, collectorProjectId = null))
    assertFalse(isTaskShownByCollector(BackgroundTaskOwnerKind.PROJECT, taskProjectId = newProjectId(), collectorProjectId = null))
  }

  @Test
  fun `a project collector shows the tasks of its project and of all frames`() {
    val projectId = newProjectId()

    assertTrue(isTaskShownByCollector(BackgroundTaskOwnerKind.PROJECT, taskProjectId = projectId, collectorProjectId = projectId))
    assertTrue(isTaskShownByCollector(BackgroundTaskOwnerKind.ALL_FRAMES, taskProjectId = null, collectorProjectId = projectId))
    assertFalse(isTaskShownByCollector(BackgroundTaskOwnerKind.PROJECT, taskProjectId = newProjectId(), collectorProjectId = projectId))
    assertFalse(isTaskShownByCollector(BackgroundTaskOwnerKind.PROJECT, taskProjectId = null, collectorProjectId = projectId))
    assertFalse(isTaskShownByCollector(BackgroundTaskOwnerKind.GUESS, taskProjectId = null, collectorProjectId = projectId))
  }

  @Test
  fun `a collector of the own tasks shows the tasks of its project only`() {
    val projectId = newProjectId()

    assertTrue(isOwnTask(BackgroundTaskOwnerKind.PROJECT, taskProjectId = projectId, collectorProjectId = projectId))
    assertFalse(isOwnTask(BackgroundTaskOwnerKind.PROJECT, taskProjectId = newProjectId(), collectorProjectId = projectId))
    assertFalse(isOwnTask(BackgroundTaskOwnerKind.PROJECT, taskProjectId = null, collectorProjectId = projectId))
    assertFalse(isOwnTask(BackgroundTaskOwnerKind.ALL_FRAMES, taskProjectId = null, collectorProjectId = projectId))
    assertFalse(isOwnTask(BackgroundTaskOwnerKind.GUESS, taskProjectId = null, collectorProjectId = projectId))
  }

  private suspend fun runAndCaptureTask(owner: BackgroundTaskOwner): StoredTask {
    return runAndCaptureTask { title, action -> withBackgroundProgress(owner, title, action) }
  }

  /**
   * Runs a background progress with [start], and returns the task that the progress stores while it runs.
   */
  private suspend fun runAndCaptureTask(
    start: suspend (title: String, action: suspend CoroutineScope.() -> StoredTask) -> StoredTask,
  ): StoredTask {
    val title = "BackgroundTaskOwnerTest task ${UID.random()}"
    return start(title) {
      var stored: StoredTask? = null
      waitUntil("The task '$title' should be stored", timeout = TIMEOUT) {
        stored = TaskStorage.getInstance().tasks.value.values
          .firstOrNull { it.info.title == title }
          ?.let { StoredTask(it.info.ownerKind, it.info.projectId) }
        stored != null
      }
      checkNotNull(stored)
    }
  }

  private fun newProjectId(): ProjectId = ProjectId.deserializeFromString(UID.random().toString())

  private data class StoredTask(val ownerKind: BackgroundTaskOwnerKind, val projectId: ProjectId?)
}

private val TIMEOUT = 10.seconds
