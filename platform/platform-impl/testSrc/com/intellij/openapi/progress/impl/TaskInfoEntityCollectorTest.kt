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
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Pins down why [TaskInfoEntity.projectId] is a plain value and not a ref to [ProjectEntity]:
 * a project's entity may legitimately be deleted and re-created with the same id (both peers create
 * one; in IJ Light the id is re-bound on connect), and a `CASCADE_DELETE_BY` ref silently wiped the
 * tasks on every such replacement. Cleanup is explicit instead — see [removeTasksForUnregisteredProjects].
 */
@TestApplication
@Suppress("DEPRECATION")
internal class TaskInfoEntityCollectorTest {

  @Test
  fun `removes task info entities when project entity is removed`(): Unit = runBlocking {
    withKernel {
      registerEntityTypes()

      val projectId = newProjectId()
      val projectEntity = createProjectEntity(projectId)
      val taskInfoEntity = createTaskInfoEntity(projectId)

      assertTrue(taskInfoEntity.exists())

      change {
        projectEntity.delete()
      }
      removeTasksForUnregisteredProjects(setOf(projectId))

      waitUntil("TaskInfoEntity should be removed after ProjectEntity deregistration", timeout = 5.seconds) {
        !taskInfoEntity.exists() && entities(TaskInfoEntity.ProjectIdType, projectId).isEmpty()
      }
    }
  }

  @Test
  fun `keeps task info entities when project entity is replaced with the same id`(): Unit = runBlocking {
    withKernel {
      registerEntityTypes()

      val projectId = newProjectId()
      val projectEntity = createProjectEntity(projectId)
      val taskInfoEntity = createTaskInfoEntity(projectId)

      assertTrue(taskInfoEntity.exists())

      val replacementProjectEntity = change {
        projectEntity.delete()
        ProjectEntity.new {
          it[ProjectEntity.ProjectIdValue] = projectId
        }
      }
      removeTasksForUnregisteredProjects(setOf(projectId))

      assertTrue(taskInfoEntity.exists(), "TaskInfoEntity should survive same-id ProjectEntity replacement")

      change {
        replacementProjectEntity.delete()
      }
      removeTasksForUnregisteredProjects(setOf(projectId))

      waitUntil("TaskInfoEntity should be removed after the replacement ProjectEntity is removed", timeout = 5.seconds) {
        !taskInfoEntity.exists() && entities(TaskInfoEntity.ProjectIdType, projectId).isEmpty()
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
