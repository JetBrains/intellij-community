// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.progress.rpc

import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.NlsContexts.ProgressTitle
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.suspender.TaskSuspension
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.lite.LiteRemoteApiProviderService
import com.intellij.platform.util.progress.ProgressState
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

/**
 * The RPC surface of the task-progress feature: a frontend observes the backend's tasks through
 * [activeTasks] and drives them through the command methods.
 *
 * This is the only cross-process channel of the feature. Each side stores its tasks in its own local
 * Rhizome DB (see `TaskStorage`); nothing task-related is synchronized through shared kernel changes
 * anymore — that synchronous sync (one shared transaction per progress tick, plus the `ProjectEntity`
 * reference it dragged along) was a recurring deadlock source in Remote Development.
 */
@ApiStatus.Internal
@Rpc
interface TaskInfoApi : RemoteApi<Unit> {

  /**
   * All tasks alive on the backend, as an event stream: a [TaskInfoEvent.TaskAdded] per task already
   * running or started later, followed by its update events, terminated by [TaskInfoEvent.TaskRemoved].
   * [RemoteTaskId]s are stable for the lifetime of the backend process and shared across subscriptions,
   * so they can be passed to the command methods.
   */
  suspend fun activeTasks(): Flow<TaskInfoEvent>

  /** No-op when the task is unknown (already finished) or not cancellable. */
  suspend fun cancelTask(taskId: RemoteTaskId)

  /** No-op when the task is unknown, not suspendable, or not running. */
  suspend fun pauseTask(taskId: RemoteTaskId, reason: @ProgressText String?)

  /** No-op when the task is unknown or not paused. */
  suspend fun resumeTask(taskId: RemoteTaskId)

  companion object {
    /** Suspends until the RPC transport is connected (see [LiteRemoteApiProviderService]). */
    suspend fun awaitInstance(): TaskInfoApi {
      return LiteRemoteApiProviderService.awaitConnectionAndResolve(remoteApiDescriptor<TaskInfoApi>())
    }
  }
}

/** Identifies one backend task across [TaskInfoApi.activeTasks] events and command calls. */
@ApiStatus.Internal
@Serializable
@JvmInline
value class RemoteTaskId(val value: Long)

@ApiStatus.Internal
@Serializable
sealed interface TaskInfoEvent {

  val taskId: RemoteTaskId

  /**
   * A task exists on the backend. [projectId] is `null` for tasks of the default project; a non-null id
   * refers to the project shared between the peers, resolvable on the frontend via
   * [com.intellij.platform.project.findProjectOrNull].
   */
  @Serializable
  data class TaskAdded(
    override val taskId: RemoteTaskId,
    val projectId: ProjectId?,
    val title: @ProgressTitle String,
    val cancellation: TaskCancellation,
    val suspension: TaskSuspension,
    val status: TaskStatus,
    val visibleInStatusBar: Boolean,
  ) : TaskInfoEvent

  @Serializable
  data class TaskRemoved(override val taskId: RemoteTaskId) : TaskInfoEvent

  @Serializable
  data class ProgressChanged(override val taskId: RemoteTaskId, val state: ProgressState?) : TaskInfoEvent

  @Serializable
  data class StatusChanged(override val taskId: RemoteTaskId, val status: TaskStatus) : TaskInfoEvent

  @Serializable
  data class SuspensionChanged(override val taskId: RemoteTaskId, val suspension: TaskSuspension) : TaskInfoEvent
}
