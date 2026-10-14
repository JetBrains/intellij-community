// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskHandle
import com.intellij.platform.ide.progress.TaskStorage
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.project.projectIdOrNull
import com.intellij.platform.project.registerProjectIdAlias
import com.intellij.platform.project.setNewProjectId
import com.intellij.testFramework.closeProjectAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.withProjectAsync
import fleet.util.UID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A task keeps its project as a plain id. [TaskStorage] removes the tasks of a project when the project is disposed.
 */
@TestApplication
internal class TaskInfoEntityCollectorTest {

  @Test
  fun `closing a project removes its tasks and keeps the other tasks`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val storage = TaskStorage.getInstance()

    openProject(tempDir.resolve("open")).useProjectAsync { openProject ->
      lateinit var closedTask: TaskHandle
      val closedProject = openProject(tempDir.resolve("closed")).withProjectAsync { closedTask = addTask(storage, it) }
      val openTask = addTask(storage, openProject)
      val defaultTask = addTask(storage, ProjectManager.getInstance().defaultProject)

      val idResolvesInProjectClosed = AtomicBoolean()
      val connection = ApplicationManager.getApplication().messageBus.connect()
      try {
        connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
          override fun projectClosed(project: Project) {
            if (project === closedProject) {
              idResolvesInProjectClosed.set(project.projectIdOrNull()?.findProjectOrNull() === project)
            }
          }
        })

        closedProject.closeProjectAsync()

        assertTrue(idResolvesInProjectClosed.get(), "The project id must still resolve in projectClosed")
        assertTrue(closedTask.removed.isCompleted, "The task of the closed project must be removed")
        assertFalse(openTask.removed.isCompleted, "The task of an open project must stay")
        assertFalse(defaultTask.removed.isCompleted, "The task of the default project must stay")
      }
      finally {
        connection.disconnect()
        storage.removeTask(closedTask)
        storage.removeTask(openTask)
        storage.removeTask(defaultTask)
      }
    }
  }

  @Test
  fun `disposing a project that never opened removes its tasks`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val storage = TaskStorage.getInstance()
    val project = loadProject(tempDir.resolve("loaded"))
    val task = addTask(storage, project)

    val projectClosed = AtomicBoolean()
    val connection = ApplicationManager.getApplication().messageBus.connect()
    try {
      connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
        override fun projectClosed(closedProject: Project) {
          if (closedProject === project) projectClosed.set(true)
        }
      })

      ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project)

      assertTrue(project.isDisposed, "The project must be disposed")
      assertFalse(projectClosed.get(), "A project that never opened must not fire projectClosed")
      assertTrue(task.removed.isCompleted, "The task of the disposed project must be removed")
      assertFalse(task.info.id in storage.tasks.value, "The storage must not keep the task of the disposed project")
    }
    finally {
      connection.disconnect()
      storage.removeTask(task)
    }
  }

  @Test
  fun `a task added to a disposed project is removed at once`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val storage = TaskStorage.getInstance()
    val project = loadProject(tempDir.resolve("disposed"))
    ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project)

    val task = addTask(storage, project)

    assertTrue(task.removed.isCompleted, "The task of a disposed project must be removed")
    assertFalse(task.info.id in storage.tasks.value, "The storage must not keep the task of a disposed project")
  }

  private suspend fun loadProject(dir: Path): Project {
    Files.createDirectories(dir.resolve(Project.DIRECTORY_STORE_FOLDER))
    return withContext(Dispatchers.IO) { ProjectManagerEx.getInstanceEx().loadProject(dir) }
  }

  @Test
  fun `closing a project removes a task that keeps an alias id of the project`(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val storage = TaskStorage.getInstance()

    lateinit var aliasTask: TaskHandle
    val project = openProject(tempDir.resolve("rebound")).withProjectAsync { aliasTask = addTask(storage, it) }
    try {
      project.useProjectAsync {
        val aliasId = checkNotNull(aliasTask.info.projectId)
        setNewProjectId(it, ProjectId.deserializeFromString(UID.random().toString()))
        registerProjectIdAlias(it, aliasId)

        assertTrue(aliasId.findProjectOrNull() === it, "The alias id must resolve to the project")
        assertFalse(aliasTask.removed.isCompleted, "The task must stay while the project is open")
      }

      assertTrue(aliasTask.removed.isCompleted, "The task with the alias id must be removed when the project closes")
    }
    finally {
      storage.removeTask(aliasTask)
    }
  }

  private suspend fun openProject(dir: Path): Project {
    val options = createTestOpenProjectOptions(runPostStartUpActivities = false).copy(isNewProject = true)
    return checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(dir, options))
  }

  private fun addTask(storage: TaskStorage, project: Project): TaskHandle {
    return storage.addTask(project, "task of $project", TaskCancellation.nonCancellable(), TaskSuspension.NonSuspendable,
                           visibleInStatusBar = true)
  }
}
