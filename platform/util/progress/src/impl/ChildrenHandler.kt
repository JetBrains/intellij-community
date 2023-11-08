// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.progress.impl

import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal class ChildrenHandler<T>(
  private val cs: CoroutineScope,
  private val defaultState: T,
  private val reduce: (childStates: List<T>) -> T?,
) {

  val progressState: MutableStateFlow<FractionState<T>> = MutableStateFlow(FractionState(-1.0, defaultState))

  val progressUpdates: Flow<FractionState<T>> = progressState.takeWhile {
    cs.isActive
  }

  // Ordered map is used to sort by update time, the latest updated child would be the last entry in the map.
  private val children: MutableStateFlow<Children<T>> = MutableStateFlow(Children(0.0, persistentMapOf()))

  private data class Children<T>(
    val completed: Double, // [0.0; 1.0]
    val active: PersistentMap<Any, FractionState<T>>,
  )

  init {
    cs.launch(Dispatchers.Unconfined) {
      children.drop(1).collect { (completed, active) ->
        progressState.value = handleChildren(completed, active.values)
      }
    }
    cs.coroutineContext.job.invokeOnCompletion {
      children.value = Children(1.0, persistentMapOf())
      progressState.value = FractionState(1.0, defaultState)
    }
  }

  fun applyChildUpdates(step: BaseProgressReporter, childUpdates: Flow<T>) {
    applyChildUpdatesInner(step, 0.0, childUpdates.map {
      FractionState(-1.0, it)
    })
  }

  fun applyChildUpdates(step: BaseProgressReporter, duration: Double, childUpdates: Flow<FractionState<T>>) {
    require(0.0 < duration && duration <= 1.0) {
      "Duration is expected to be a value in (0.0; 1.0], got $duration"
    }
    applyChildUpdatesInner(step, duration, childUpdates)
  }

  private fun applyChildUpdatesInner(
    step: BaseProgressReporter,
    duration: Double,
    childUpdates: Flow<FractionState<T>>,
  ) {
    cs.launch(Dispatchers.Unconfined) {
      val childUpdateCollector = launch {
        childUpdates.collect { childUpdate: FractionState<T> ->
          val scaledUpdate = childUpdate.copy(
            fraction = if (duration == 0.0) -1.0 else duration * childUpdate.fraction.coerceAtLeast(.0),
          )
          children.update { children ->
            // put the latest update to the end of [active] map
            children.copy(active = children.active.remove(step).put(step, scaledUpdate))
          }
        }
      }
      step.awaitCompletion()
      childUpdateCollector.cancelAndJoin()
      children.update { (completed, active) ->
        Children(
          completed = completed + duration,
          active = active.remove(step),
        )
      }
    }
  }

  private fun handleChildren(completed: Double, updates: ImmutableCollection<FractionState<T>>): FractionState<T> {
    return if (updates.isEmpty()) {
      FractionState(
        fraction = if (completed == 0.0) -1.0 else completed,
        state = defaultState,
      )
    }
    else {
      val latestFirst: List<T> = updates.map { it.state }.asReversed()
      FractionState(
        fraction = totalFraction(completed, updates),
        state = reduce(latestFirst) ?: defaultState
      )
    }
  }
}
