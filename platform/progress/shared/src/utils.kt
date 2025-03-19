// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress

import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.util.progress.ProgressState
import fleet.kernel.onDispose
import fleet.kernel.rete.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a query for retrieving all active tasks in the system.
 * To receive task updates, use [updates]
 *
 * @see TaskSupport for more info about how tasks are started
 */
val activeTasks: Query<TaskInfoEntity>
  @ApiStatus.Internal
  get() = TaskInfoEntity.each()

/**
 * Returns a query that provides updates to the progress state of a task.
 * For more info about progress state see [TaskInfoEntity.progressState]
 *
 * For a finite flow use [asFiniteFlow] extension
 */
val TaskInfoEntity.updates: Query<ProgressState>
  @ApiStatus.Internal
  get() = asQuery()[TaskInfoEntity.ProgressStateType]

/**
 * Returns a query to retrieve the statuses of a task.
 * For more info about task status see [TaskInfoEntity.taskStatus]
 *
 * For a finite flow use [asFiniteFlow] extension
 */
val TaskInfoEntity.statuses: Query<TaskStatus>
  @ApiStatus.Internal
  get() = asQuery()[TaskInfoEntity.TaskStatusType]

/**
 * Returns a query that provides changes in the suspendable status of the task.
 * For more info about suspendable see [TaskSuspension].
 */
val TaskInfoEntity.suspensionState: Query<TaskSuspension>
  @ApiStatus.Internal
  get() = asQuery()[TaskInfoEntity.TaskSuspensionType]

/**
 * Converts a query result into a finite flow that emits results as long as the specified entity is alive.
 * The flow will terminate when the entity is disposed
 *
 * @param T the type of the query result.
 * @param entity the task information entity associated with the query. It will determine the lifespan of the flow.
 * @return a finite flow of query results that will complete when the entity is disposed.
 */
@ApiStatus.Internal
fun <T> Query<T>.asFiniteFlow(entity: TaskInfoEntity, rete: Rete): Flow<T> {
  val entityIsAlive = MutableStateFlow(true)
  entity.onDispose(rete) { entityIsAlive.value = false }

  return this.matchesFlow()
    .combine(entityIsAlive) { state, isAlive -> state.takeIf { isAlive } }
    .takeWhile { it != null }
    .mapNotNull { it?.value }
}
