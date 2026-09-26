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
import fleet.kernel.rete.launchOnEach
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
      activeTasks.launchOnEach { task ->
        val taskId = registry.register(task)
        try {
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

  // register/unregister must be mutually atomic: resolving an id and adjusting its refcount as separate
  // steps lets a concurrent last-unregister drop the id mapping under a subscription that just adopted it
  private val lock = Any()
  private val tasksById = ConcurrentHashMap<RemoteTaskId, TaskInfoEntity>() // concurrent so [task] reads without [lock]
  private val idsByTask = HashMap<TaskInfoEntity, RemoteTaskId>()
  private val subscriptionsById = HashMap<RemoteTaskId, Int>()

  fun register(task: TaskInfoEntity): RemoteTaskId = synchronized(lock) {
    val taskId = idsByTask.getOrPut(task) {
      RemoteTaskId(counter.incrementAndGet()).also { tasksById[it] = task }
    }
    subscriptionsById.merge(taskId, 1, Int::plus)
    taskId
  }

  fun unregister(taskId: RemoteTaskId): Unit = synchronized(lock) {
    val remaining = (subscriptionsById[taskId] ?: return) - 1
    if (remaining > 0) {
      subscriptionsById[taskId] = remaining
    }
    else {
      subscriptionsById.remove(taskId)
      tasksById.remove(taskId)?.let { idsByTask.remove(it) }
    }
  }

  fun task(taskId: RemoteTaskId): TaskInfoEntity? = tasksById[taskId]
}
