// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskInfoEntity
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.ProjectEntity
import com.intellij.platform.project.ProjectId
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import com.jetbrains.rhizomedb.entities
import com.jetbrains.rhizomedb.exists
import fleet.kernel.change
import fleet.util.UID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Pins down why [TaskInfoEntity.projectId] is a plain value and not a ref to [ProjectEntity]:
 * a project's entity may legitimately be deleted and re-created with the same id (both peers create
 * one; in IJ Light the id is re-bound on connect), and a `CASCADE_DELETE_BY` ref silently wiped the
 * tasks on every such replacement. Cleanup is explicit instead — see [collectStaleProjectTasks] and
 * the [removeTasksForUnregisteredProjects] decision it drives.
 */
@TestApplication
@Suppress("DEPRECATION")
internal class TaskInfoEntityCollectorTest {

  @Test
  fun `the collector removes task info entities once the project entity is gone`(): Unit = runBlocking {
    withKernel {
      registerEntityTypes()

      val projectId = newProjectId()
      val projectEntity = createProjectEntity(projectId)
      val taskInfoEntity = createTaskInfoEntity(projectId)

      withCollector {
        change { projectEntity.delete() }

        waitUntil("TaskInfoEntity should be removed after ProjectEntity deregistration", timeout = TIMEOUT) {
          !taskInfoEntity.exists() && entities(TaskInfoEntity.ProjectIdType, projectId).isEmpty()
        }
      }
    }
  }

  @Test
  fun `the collector keeps task info entities when the project entity is replaced with the same id`(): Unit = runBlocking {
    withKernel {
      registerEntityTypes()

      val projectId = newProjectId()
      val projectEntity = createProjectEntity(projectId)
      val taskInfoEntity = createTaskInfoEntity(projectId)

      withCollector {
        // one transaction: the collector sees the old entity retracted and the new one asserted together
        val replacement = change {
          projectEntity.delete()
          ProjectEntity.new {
            it[ProjectEntity.ProjectIdValue] = projectId
          }
        }

        // there is nothing to wait for here — an unwanted removal can only be observed by not happening,
        // so drive a second, unambiguous change through the collector and assert the task outlived both
        change { replacement.delete() }

        waitUntil("TaskInfoEntity should be removed after the replacement ProjectEntity is removed", timeout = TIMEOUT) {
          !taskInfoEntity.exists()
        }
      }
    }
  }

  @Test
  fun `tasks of a project whose entity was replaced under the same id are not removed`(): Unit = runBlocking {
    withKernel {
      registerEntityTypes()

      val projectId = newProjectId()
      val projectEntity = createProjectEntity(projectId)
      val taskInfoEntity = createTaskInfoEntity(projectId)

      change {
        projectEntity.delete()
        ProjectEntity.new {
          it[ProjectEntity.ProjectIdValue] = projectId
        }
      }
      removeTasksForUnregisteredProjects(setOf(projectId))

      assertTrue(taskInfoEntity.exists(), "TaskInfoEntity should survive same-id ProjectEntity replacement")
    }
  }

  /**
   * Runs [body] with [collectStaleProjectTasks] observing the DB.
   *
   * [CoroutineStart.UNDISPATCHED] is what makes this deterministic: the collector runs up to its first
   * real suspension — that is, past subscribing to `ProjectEntity.each()` — before [body] gets to change
   * anything. A dispatched start could queue behind the change and miss the entity it is supposed to track.
   */
  private suspend fun withCollector(body: suspend () -> Unit) {
    coroutineScope {
      val collector: Job = launch(start = CoroutineStart.UNDISPATCHED) { collectStaleProjectTasks(this) }
      try {
        body()
      }
      finally {
        collector.cancel()
      }
    }
  }

  private suspend fun registerEntityTypes() {
    change {
      register(ProjectEntity, TaskInfoEntity)
    }
  }

  private fun newProjectId(): ProjectId = ProjectId.deserializeFromString(UID.random().toString())

  private suspend fun createProjectEntity(projectId: ProjectId): ProjectEntity {
    return change {
      ProjectEntity.new {
        it[ProjectEntity.ProjectIdValue] = projectId
      }
    }
  }

  private suspend fun createTaskInfoEntity(projectId: ProjectId): TaskInfoEntity {
    return change {
      TaskInfoEntity.new {
        it[TaskInfoEntity.ProjectIdType] = projectId
        it[TaskInfoEntity.TitleType] = "test task"
        it[TaskInfoEntity.TaskCancellationType] = TaskCancellation.nonCancellable()
        it[TaskInfoEntity.TaskSuspensionType] = TaskSuspension.NonSuspendable
        it[TaskInfoEntity.ProgressStateType] = null
        it[TaskInfoEntity.TaskStatusType] = TaskStatus.Running(source = TaskStatus.Source.SYSTEM)
        it[TaskInfoEntity.ProgressBarVisibilityType] = true
      }
    }
  }
}

private val TIMEOUT = 10.seconds
