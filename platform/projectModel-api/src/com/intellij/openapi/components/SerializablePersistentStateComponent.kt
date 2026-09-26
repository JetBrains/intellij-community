// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.util.xmlb.SettingsInternalApi
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

/**
 * Use the [updateState] method to atomically change the persistent state.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/persisting-state-of-components.html">Persisting State of Components
 *  (IntelliJ Platform Docs)</a>
 */
abstract class SerializablePersistentStateComponent<T : Any>(
  // used via STATE_HANDLE
  @Suppress("unused") private var state: T,
) : PersistentStateComponentWithModificationTracker<T> {
  companion object {
    private val STATE_HANDLE: VarHandle
    private val TIMESTAMP_HANDLE: VarHandle

    init {
      val lookup = MethodHandles.privateLookupIn(
        /* targetClass = */ SerializablePersistentStateComponent::class.java,
        /* caller = */ MethodHandles.lookup(),
      )
      STATE_HANDLE = lookup.findVarHandle(
        /* recv = */ SerializablePersistentStateComponent::class.java,
        /* name = */ "state",
        /* type = */ Any::class.java,
      )
      TIMESTAMP_HANDLE = lookup.findVarHandle(
        /* recv = */ SerializablePersistentStateComponent::class.java,
        /* name = */ "timestamp",
        /* type = */ Long::class.javaPrimitiveType,
      )
    }

    @PublishedApi
    internal fun compareAndSet(component: SerializablePersistentStateComponent<*>, prev: Any, next: Any?): Boolean {
      if (STATE_HANDLE.weakCompareAndSet(component, prev, next)) {
        TIMESTAMP_HANDLE.getAndAdd(component, 1L)
        return true
      }
      return false
    }
  }

  // used via TIMESTAMP_HANDLE
  @Suppress("unused")
  private var timestamp = 0L

  @Suppress("UNCHECKED_CAST")
  final override fun getState(): T = STATE_HANDLE.getVolatile(this) as T

  /**
   * Replaces the current state without updating [getStateModificationCount].
   *
   * This API is intended for state-loading paths (for example, [loadState]) where the store controls
   * modification tracking separately.
   *
   * For runtime state changes, use [updateState]. Calling this method directly may cause persistence
   * to skip saving because the modification count is unchanged.
   */
  @SettingsInternalApi
  @Internal
  fun setState(newState: T) {
    STATE_HANDLE.setVolatile(this, newState)
  }

  // method cannot be marked as Internal because it is the only way to be notified when state is loaded
  override fun loadState(state: T) {
    @OptIn(SettingsInternalApi::class)
    setState(state)
  }

  final override fun getStateModificationCount(): Long = TIMESTAMP_HANDLE.getVolatile(this) as Long

  /**
   * Atomically updates the component state using the provided update function.
   * The update is performed in a thread-safe manner using compare-and-set operations.
   *
   * Similar to [java.util.concurrent.atomic.AtomicReference.updateAndGet].
   *
   * Example usage:
   * ```
   * class MyComponent : SerializablePersistentStateComponent<MyState>(MyState()) {
   *   fun addItem(item: String) {
   *     updateState { currentState ->
   *       currentState.copy(items = currentState.items + item)
   *     }
   *   }
   * }
   * ```
   *
   * Warning: The update function may be called multiple times if there are concurrent modifications.
   * Ensure the function is idempotent and has no side effects.
   *
   * @param updateFunction A function that takes the current state and returns the new state.
   *                      Should be pure and have no side effects.
   * @return The new state after the update is applied
   * @see java.util.concurrent.atomic.AtomicReference.updateAndGet
   */
  protected inline fun updateState(updateFunction: (currentState: T) -> T): T {
    var prev = getState()
    var next: T? = null
    while (true) {
      if (next == null) {
        next = updateFunction(prev)
      }
      if (compareAndSet(this, prev, next)) {
        return next
      }

      val haveNext = prev === getState().also { prev = it }
      if (!haveNext) {
        next = null
      }
    }
  }
}
