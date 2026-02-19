// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.coroutines.flow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong

/**
 * A mutable state that supports incremental updates and taking snapshots.
 * 
 * Clients of [IncrementalUpdateFlowProducer] should implement this interface
 * and provide an instance of the implementation.
 * 
 * Both functions are guaranteed to be called under a mutex,
 * so they should not call [IncrementalUpdateFlowProducer.handleUpdate]
 * to avoid deadlocks.
 */
@ApiStatus.Internal
interface MutableStateWithIncrementalUpdates<U : Any> {
  /**
   * Applies the given update to the state.
   * 
   * Should not emit any recursive updates to avoid deadlocks.
   * In other words, calling [IncrementalUpdateFlowProducer.handleUpdate] from this function,
   * directly or indirectly, is forbidden.
   *
   * If the update is applied, normally this function should return the update unchanged.
   *
   * If the update is a no-op for some reason (e.g., the event is outdated),
   * the implementation may choose to return `null` to indicate that the event should not be forwarded
   * to the output flow to save on traffic.
   *
   * In exotic cases, the implementation may decide to modify the update somehow and return the modified copy.
   * Then the modified copy will be forwarded.
   *
   * In the case when this function threw an exception, the update will be forwarded to the output flow as-is.
   */
  suspend fun applyUpdate(update: U): U?

  /**
   * Takes a snapshot of the current state.
   * 
   * It is allowed to return an empty list, representing the initial state.
   * It can happen both when there were no updates yet,
   * or there were some, but they were no-op or canceled each other (like an undo action).
   * 
   * Applying the returned updates to an instance of the initial state
   * should mutate it exactly into the same state as this one.
   *
   * This function should not mutate the state in any way.
   * It follows that calling [IncrementalUpdateFlowProducer.handleUpdate] from this function,
   * directly or indirectly, is forbidden.
   */
  suspend fun takeSnapshot(): List<U>
}

/**
 * A generic implementation of a flow of some mutable state snapshot followed by incremental updates to this state.
 * 
 * Intended to be used for mutable states with the following properties:
 * 
 * 1. There's a way to create an initial, "empty" state.
 * 2. There's a sequence of updates that mutate the state.
 * 3. There's a way to capture a snapshot of the state represented as a single update or a sequence of updates.
 * 4. There's a need to expose the state as a Flow that emits first the snapshot and then incremental updates to it.
 * 
 * A common use case is in remdev scenarios when the state is updated on the backend,
 * and the frontend can connect at any moment and needs to receive the full state first
 * and then incremental updates to that state.
 */
@ApiStatus.Internal
class IncrementalUpdateFlowProducer<U : Any>(private val state: MutableStateWithIncrementalUpdates<U>) {
  private val updateVersion = AtomicLong()
  private val lock = Mutex()
  private var appliedVersion = 0L
  // A replay buffer is only needed so that the collector can start immediately.
  private val outputFlow = MutableSharedFlow<Update<U>>(replay = 1)

  /**
   * Applies the given update to the state and forwards the update to the output flows.
   * 
   * This function calls [MutableStateWithIncrementalUpdates.applyUpdate] under a mutex
   * to update the state.
   * Then the update is forwarded to the output flows, if any, unless `applyUpdate` returned `null`.
   * 
   * **Exception handling**
   * 
   * If `applyUpdate` throws an exception, it'll be rethrown by this function,
   * but first the update will be forwarded to the output flows regardless.
   * This allows to support the use case when the local state gets corrupted for some reason,
   * but the subscribers somehow manage to continue consuming updates correctly.
   * In this scenario new clients will likely receive the corrupted state (unless it manages to fix itself somehow),
   * but at least the existing clients continue to receive updates without interruption.
   * 
   * **Leak avoidance guarantee**
   * 
   * The reference to the given update is _not_ kept after it has been forwarded to the output flows.
   * 
   * **Concurrency**
   * 
   * This function is thread-safe in the sense that it can be called from any thread,
   * but it should not be called concurrently, because it's not guaranteed then
   * that the updates will be emitted to the output flows in the same order
   * they were applied to the mutable state.
   */
  suspend fun handleUpdate(update: U) {
    val versionedUpdate = VersionedUpdate(update, updateVersion.incrementAndGet())
    var updateToForward: VersionedUpdate<U>? = versionedUpdate
    try {
      lock.withLock {
        val modifiedUpdate = state.applyUpdate(update)
        updateToForward = when {
          modifiedUpdate === update -> versionedUpdate // nothing changed, avoid unneeded copying
          modifiedUpdate != null -> versionedUpdate.copy(update = modifiedUpdate)
          else -> null // the update is a no-op, don't forward
        }
        appliedVersion = versionedUpdate.version
      }
    }
    finally {
      if (updateToForward != null) {
        outputFlow.emit(updateToForward)
        // Clear the reference to the update from the replay buffer now, to avoid leaks.
        outputFlow.emit(fakeUpdate())
      }
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun fakeUpdate(): Update<U> = FakeUpdate as Update<U>

  /**
   * Returns a cold flow of the state snapshot followed by incremental updates.
   * 
   * The returned flow, when collected, will instantly call [MutableStateWithIncrementalUpdates.takeSnapshot]
   * and emit the updates returned by it, if any.
   * Then it will continue emitting incremental updates as they come in to [handleUpdate].
   * 
   * **Consistency guarantee**
   * 
   * It is guaranteed that no incremental updates will be missed after taking the snapshot,
   * and that no incremental updates applied before taking the snapshot will be emitted.
   * To guarantee this, both [MutableStateWithIncrementalUpdates.takeSnapshot]
   * and [MutableStateWithIncrementalUpdates.applyUpdate] are called under the same mutex.
   * The said mutex is _not_ held when emitting events, however,
   * so calling [handleUpdate] from a collector of the returned flow is safe.
   */
  fun getIncrementalUpdateFlow(): Flow<U> = flow {
    var initialStateVersion = -1L
    outputFlow.collect { update ->
      if (initialStateVersion == -1L) {
        val initialState = lock.withLock {
          VersionedUpdate(state.takeSnapshot(), appliedVersion)
        }
        for (update in initialState.update) {
          emit(update)
        }
        initialStateVersion = initialState.version
      }
      if (update is VersionedUpdate<U> && update.version > initialStateVersion) {
        emit(update.update)
      }
    }
  }
}

private sealed class Update<U>
private data class VersionedUpdate<U>(val update: U, val version: Long) : Update<U>()
private data object FakeUpdate : Update<Any>()
