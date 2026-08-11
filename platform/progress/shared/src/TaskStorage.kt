// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.jetbrains.rhizomedb.ChangeScope
import com.jetbrains.rhizomedb.exists
import fleet.kernel.change
import fleet.kernel.delete
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Stores the tasks of this process in its local Rhizome DB.
 *
 * Tasks are always local: another process observes them over RPC
 * (see [com.intellij.platform.ide.progress.rpc.TaskInfoApi]), never through shared kernel changes.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class TaskStorage {

  /**
   * Adds a new task to the storage and returns the created [TaskInfoEntity].
   *
   * @param project in which frame the progress should be shown
   * @param title The title of the task.
   * @param cancellation Specifies if the task can be canceled.
   * @param visibleInStatusBar Specifies if the task should be fully visible in the status bar, or just in the number of running tasks
   *        and popup with the full list of tasks.
   * @return The created [TaskInfoEntity].
   */
  suspend fun addTask(
    project: Project,
    title: @ProgressTitle String,
    cancellation: TaskCancellation,
    suspendable: TaskSuspension,
    visibleInStatusBar: Boolean,
  ): TaskInfoEntity? {
    var taskInfoEntity: TaskInfoEntity? = null
    try {
      return withKernel {
        val projectEntity = if (!project.isDefault) project.asEntity() else null
        taskInfoEntity = change {
          if (projectEntity?.exists() == false) {
            LOG.warn("The task info entity for \"${title}\" wasn't created, because $project does not exist anymore")
            return@change null
          }

          TaskInfoEntity.new {
            it[TaskInfoEntity.ProjectEntityType] = projectEntity
            it[TaskInfoEntity.TitleType] = title
            it[TaskInfoEntity.TaskCancellationType] = cancellation
            it[TaskInfoEntity.TaskSuspensionType] = suspendable
            it[TaskInfoEntity.ProgressStateType] = null
            it[TaskInfoEntity.TaskStatusType] = TaskStatus.Running(source = TaskStatus.Source.SYSTEM)
            it[TaskInfoEntity.ProgressBarVisibilityType] = visibleInStatusBar
          }
        }
        return@withKernel taskInfoEntity
      }
    }
    catch (ex: Exception) {
      // Ensure that task is deleted if exception happened during creation (e.g. CancellationException on withContext exit)
      withContext(NonCancellable) {
        taskInfoEntity?.let { removeTask(it) }
      }
      throw ex
    }
  }

  /**
   * Removes a task from Rhizome DB.
   * NOTE: this doesn't cancel a running task, to cancel a task use [TaskManager.cancelTask]
   *
   * @param taskInfoEntity The task to be removed.
   */
  suspend fun removeTask(taskInfoEntity: TaskInfoEntity): Unit = withKernel {
    change {
      delete(taskInfoEntity)
    }
  }

  /**
   * Updates a [TaskInfoEntity] in the storage using provided [updater]
   * It's guaranteed that [taskInfoEntity] exists when [updater] is invoked
   *
   * @param taskInfoEntity The task to be updated.
   * @param updater A lambda provided with a [ChangeScope] receiver to modify the task information.
   * @return Unit
   */
  suspend fun updateTask(taskInfoEntity: TaskInfoEntity, updater: ChangeScope.() -> Unit): Unit = withKernel {
    change {
      if (!taskInfoEntity.exists()) return@change
      updater()
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(): TaskStorage = service()

    private val LOG = logger<TaskStorage>()
  }
}
