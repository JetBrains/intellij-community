// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.platform.kernel.withKernel
import fleet.kernel.*
import org.jetbrains.annotations.ApiStatus

/**
 * Util methods for managing actively running tasks
 *
 * @see TaskInfoEntity for more info about a task
 */
@ApiStatus.Internal
object TaskManager {
  private val LOG = logger<TaskManager>()

  /**
   * Cancels a running task.
   * The method shouldn't cancel a task if it's not cancelable (see [TaskCancellation.NonCancellable])
   *
   * @param taskInfoEntity task to cancel
   */
  suspend fun cancelTask(taskInfoEntity: TaskInfoEntity) = withKernel {
    tryWithEntities(taskInfoEntity) {
      if (taskInfoEntity.cancellation is TaskCancellation.NonCancellable) {
        LOG.error("Task ${taskInfoEntity.eid} is not cancellable")
        return@tryWithEntities
      }

      taskInfoEntity.setTaskStatus(TaskStatus.CANCELED)
    }
  }

  /**
   * Pauses a running task.
   * The task can be resumed later ([resumeTask]) or canceled ([cancelTask])
   *
   * @param taskInfoEntity task to pause
   */
  suspend fun pauseTask(taskInfoEntity: TaskInfoEntity) = withKernel {
    tryWithEntities(taskInfoEntity) {
      // TODO Check that task can be suspended RDCT-1620

      taskInfoEntity.setTaskStatus(TaskStatus.PAUSED)
    }
  }

  /**
   * Resume a paused task.
   * The task has to be paused ([pauseTask]), otherwise the method won't affect it
   *
   * @param taskInfoEntity task to pause
   */
  suspend fun resumeTask(taskInfoEntity: TaskInfoEntity) = withKernel {
    tryWithEntities(taskInfoEntity) {
      taskInfoEntity.setTaskStatus(TaskStatus.RUNNING)
    }
  }

  private suspend fun TaskInfoEntity.setTaskStatus(newStatus: TaskStatus) {
    // If a task is shared, it should be updated in shared scope, otherwise it should be update in local
    change {
      if (isShared) {
        shared { trySetTaskStatus(newStatus) }
      }
      else {
        trySetTaskStatus(newStatus)
      }
    }
  }

  private fun TaskInfoEntity.trySetTaskStatus(newStatus: TaskStatus) {
    if (!canChangeStatus(from = taskStatus, to = newStatus)) {
      LOG.info("Task status cannot be changed from ${taskStatus} to $newStatus")
      return
    }

    LOG.trace { "Changing task status from $taskStatus to $newStatus" }
    taskStatus = newStatus
  }

  private fun canChangeStatus(from: TaskStatus, to: TaskStatus): Boolean {
    return when (to) {
      // Task can be resumed only if it was suspended before
      TaskStatus.RUNNING -> from == TaskStatus.PAUSED
      // Task can be suspended only if it was running before
      TaskStatus.PAUSED -> from == TaskStatus.RUNNING
      // Task can be canceled from any status
      TaskStatus.CANCELED -> true
    }
  }
}
