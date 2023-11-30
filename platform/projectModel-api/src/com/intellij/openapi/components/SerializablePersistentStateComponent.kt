// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import java.lang.invoke.MethodHandles
import java.lang.invoke.VarHandle

/**
 * Use the updateState() method to atomically change the persistent state
 */
abstract class SerializablePersistentStateComponent<T : Any>(private var state: T) : PersistentStateComponentWithModificationTracker<T> {
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

  private var timestamp = 0L

  @Suppress("UNCHECKED_CAST")
  final override fun getState(): T = STATE_HANDLE.getVolatile(this) as T

  fun setState(newState: T) {
    STATE_HANDLE.setVolatile(this, newState)
  }

  override fun loadState(state: T) {
    setState(state)
  }

  final override fun getStateModificationCount(): Long = TIMESTAMP_HANDLE.getVolatile(this) as Long

  /**
   * See [java.util.concurrent.atomic.AtomicReference.updateAndGet].
   *
   * @param updateFunction a function to merge states
   */
  protected inline fun updateState(updateFunction: (currentState: T) -> T): T {
    var prev = getState()
    var next: T? = null
    var haveNext = false
    while (true) {
      if (!haveNext) {
        next = updateFunction(prev)
      }
      if (compareAndSet(this, prev, next)) {
        return next!!
      }
      haveNext = prev === getState().also { prev = it }
    }
  }
}