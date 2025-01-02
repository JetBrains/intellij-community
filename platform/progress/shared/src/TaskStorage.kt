// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asEntity
import com.intellij.platform.project.asEntityOrNull
import com.intellij.platform.util.progress.ProgressState
import com.jetbrains.rhizomedb.ChangeScope
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the ability to control how tasks are stored in Rhizome DB
 * The implementation can decide where to store an object - in shared or local DB
 * See [fleet.kernel.change] and [fleet.kernel.shared]
 */
@ApiStatus.Internal
abstract class TaskStorage {

  /**
   * Adds a new task to the storage and returns the created [TaskInfoEntity].
   *
   * @param project in which frame the progress should be shown
   * @param title The title of the task.
   * @param cancellation Specifies if the task can be canceled.
   * @return The created [TaskInfoEntity].
   */
  suspend fun addTask(
    project: Project,
    title: String,
    cancellation: TaskCancellation,
    suspendable: TaskSuspension,
  ): TaskInfoEntity {
    var taskInfoEntity: TaskInfoEntity? = null
    try {
      return withKernel {
        val projectEntity = if (!project.isDefault) project.asEntity() else null
        taskInfoEntity = createTaskInfoEntity {
          TaskInfoEntity.new {
            it[TaskInfoEntity.ProjectEntityType] = projectEntity
            it[TaskInfoEntity.TitleType] = title
            it[TaskInfoEntity.TaskCancellationType] = cancellation
            it[TaskInfoEntity.TaskSuspensionType] = suspendable
            it[TaskInfoEntity.ProgressStateType] = null
            it[TaskInfoEntity.TaskStatusType] = TaskStatus.Running(source = TaskStatus.Source.SYSTEM)
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
   * Creates a new [TaskInfoEntity] using [provider] lambda
   * The implementation can decide whether the entity should be created locally on in the shared DB scope.
   *
   * It's guaranteed that the method is called in the correct coroutine context,
   * which includes kernel (see [withKernel])
   *
   * @param provider The provider used to create the [TaskInfoEntity].
   * @return The created [TaskInfoEntity].
   */
  protected abstract suspend fun createTaskInfoEntity(provider: ChangeScope.() -> TaskInfoEntity): TaskInfoEntity

  /**
   * Removes a task from Rhizome DB.
   * NOTE: this doesn't cancel a running task, to cancel a task use [TaskManager.cancelTask]
   *
   * @param taskInfoEntity The task to be removed.
   */
  suspend fun removeTask(taskInfoEntity: TaskInfoEntity): Unit = withKernel {
    removeTaskInfoEntity(taskInfoEntity)
  }

  /**
   * Removes the specified task from the storage.
   * The implementation should use the same scope as in [createTaskInfoEntity] to remove the entity.
   *
   * It's guaranteed that only the task created by this instance of [TaskStorage]
   * are going to be passed to this method.
   *
   * It is also guaranteed that the method is called in the correct coroutine context,
   * which includes kernel (see [withKernel])
   *
   * @param taskInfoEntity The task entity to be removed.
   */
  protected abstract suspend fun removeTaskInfoEntity(taskInfoEntity: TaskInfoEntity)

  /**
   * Updates the progress state of the given task.
   * Old state is going to be overwritten, to receive all state updates use [updates]
   *
   * @param taskInfoEntity The task to be updated.
   * @param state The new progress state to set on the task.
   * @return Unit
   */
  suspend fun updateTask(taskInfoEntity: TaskInfoEntity, state: ProgressState): Unit = withKernel {
    tryWithEntities(taskInfoEntity) {
      updateTaskInfoEntity {
        taskInfoEntity[TaskInfoEntity.ProgressStateType] = state
      }
    }
  }

  /**
   * Updates a [TaskInfoEntity] in the storage using provided [updater]
   *
   * It's guaranteed that only the task created by this instance of [TaskStorage]
   * are going to be passed to this method.
   *
   * It is also guaranteed that the method is called in the correct coroutine context,
   * which includes kernel (see [withKernel])
   *
   * @param updater A lambda provided with a [ChangeScope] receiver to modify the task information.
   */
  protected abstract suspend fun updateTaskInfoEntity(updater: ChangeScope.() -> Unit)

  companion object {
    @JvmStatic
    fun getInstance(): TaskStorage = service()
  }
}
