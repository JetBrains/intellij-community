// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.progress.backend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.platform.ide.progress.TaskInfoEntity
import com.intellij.platform.ide.progress.TaskManager
import com.intellij.platform.ide.progress.TaskStatus
import com.intellij.platform.ide.progress.activeTasks
import com.intellij.platform.ide.progress.rpc.RemoteTaskId
import com.intellij.platform.ide.progress.rpc.TaskInfoApi
import com.intellij.platform.ide.progress.rpc.TaskInfoEvent
import com.intellij.platform.ide.progress.statuses
import com.intellij.platform.ide.progress.suspensionState
import com.intellij.platform.ide.progress.updates
import com.intellij.platform.kernel.withKernel
import fleet.kernel.rete.asValuesFlow
import fleet.kernel.rete.collect
import fleet.kernel.tryWithEntities
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val LOG = logger<BackendTaskInfoApi>()

/**
 * Serves the backend's local task entities to frontends over RPC (see [TaskInfoApi]). Each subscription
 * runs its own rete collection over the local DB — the same source the backend's own status bar reads —
 * so a frontend observes exactly what the backend shows, without any shared kernel state.
 */
internal class BackendTaskInfoApi : TaskInfoApi {

  override suspend fun activeTasks(): Flow<TaskInfoEvent> = channelFlow {
    val registry = serviceAsync<BackendTaskRegistry>()

    // an RPC call carries no kernel context of its own, unlike a service coroutine scope
    @Suppress("DEPRECATION")
    withKernel {
      // A task is served for as long as it lives, so that work must not run in the rete match scope the
      // `collect` lambda gets: `Query.collect` invokes the lambda under `Match.withMatch`, which awaits
      // that scope, and a child outliving the lambda would stall the sequential collection and let only
      // the oldest task through. This scope is a sibling of the collection and still carries the kernel
      // context. `tryWithEntities` below is what ends a task's work once its entity is gone.
      val tasksScope = this

      activeTasks.collect { task ->
        tasksScope.launch {
          val taskId = registry.register(task)
          try {
            tryWithEntities(task) {
              send(TaskInfoEvent.TaskAdded(
                taskId = taskId,
                projectId = task.projectId,
                title = task.title,
                cancellation = task.cancellation,
                suspension = task.suspension,
                status = task.taskStatus,
                visibleInStatusBar = task.visibleInStatusBar,
              ))

              // progress ticks are conflated: only the freshest state matters to a UI on the other side
              launch { task.updates.asValuesFlow().conflate().collect { send(TaskInfoEvent.ProgressChanged(taskId, it)) } }
              launch { task.statuses.asValuesFlow().collect { send(TaskInfoEvent.StatusChanged(taskId, it)) } }
              launch { task.suspensionState.asValuesFlow().collect { send(TaskInfoEvent.SuspensionChanged(taskId, it)) } }

              awaitCancellation()
            }
          }
          finally {
            registry.unregister(taskId)
            withContext(NonCancellable) {
              // the subscriber may be gone already (its channel closed) — then the removal is moot anyway
              runCatching { send(TaskInfoEvent.TaskRemoved(taskId)) }
            }
          }
        }
      }
    }
  }

  override suspend fun cancelTask(taskId: RemoteTaskId) {
    val task = taskFor(taskId) ?: return
    TaskManager.cancelTask(task, TaskStatus.Source.USER)
  }

  override suspend fun pauseTask(taskId: RemoteTaskId, reason: @ProgressText String?) {
    val task = taskFor(taskId) ?: return
    TaskManager.pauseTask(task, reason, TaskStatus.Source.USER)
  }

  override suspend fun resumeTask(taskId: RemoteTaskId) {
    val task = taskFor(taskId) ?: return
    TaskManager.resumeTask(task, TaskStatus.Source.USER)
  }

  private suspend fun taskFor(taskId: RemoteTaskId): TaskInfoEntity? {
    val task = serviceAsync<BackendTaskRegistry>().task(taskId)
    if (task == null) {
      LOG.info("No live task for $taskId, the command is dropped")
    }
    return task
  }
}

/**
 * Assigns process-stable [RemoteTaskId]s to live task entities, so command calls can reference tasks
 * across (and independently of) [TaskInfoApi.activeTasks] subscriptions. Ids of the same entity are
 * shared between concurrent subscriptions.
 */
@Service(Service.Level.APP)
internal class BackendTaskRegistry {
  private val counter = AtomicLong()
  private val tasksById = ConcurrentHashMap<RemoteTaskId, TaskInfoEntity>()
  private val idsByTask = ConcurrentHashMap<TaskInfoEntity, RemoteTaskId>()
  private val subscriptionsById = ConcurrentHashMap<RemoteTaskId, Int>()

  fun register(task: TaskInfoEntity): RemoteTaskId {
    val taskId = idsByTask.computeIfAbsent(task) {
      val id = RemoteTaskId(counter.incrementAndGet())
      tasksById[id] = task
      id
    }
    subscriptionsById.merge(taskId, 1, Int::plus)
    return taskId
  }

  fun unregister(taskId: RemoteTaskId) {
    val remaining = subscriptionsById.computeIfPresent(taskId) { _, count -> (count - 1).takeIf { it > 0 } }
    if (remaining == null) {
      subscriptionsById.remove(taskId)
      tasksById.remove(taskId)?.let { idsByTask.remove(it) }
    }
  }

  fun task(taskId: RemoteTaskId): TaskInfoEntity? = tasksById[taskId]
}
