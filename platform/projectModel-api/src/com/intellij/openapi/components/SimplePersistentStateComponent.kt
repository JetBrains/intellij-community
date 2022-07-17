// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import org.jetbrains.annotations.ApiStatus.Experimental

abstract class SimplePersistentStateComponent<T : BaseState>(initialState: T) : PersistentStateComponentWithModificationTracker<T> {
  @Volatile
  private var state: T = initialState

  final override fun getState() = state

  final override fun getStateModificationCount() = state.modificationCount

  override fun loadState(state: T) {
    this.state = state
  }
}

@Experimental
abstract class SerializablePersistentStateComponent<T : Any>(initialState: T) : PersistentStateComponentWithModificationTracker<T> {
  @Volatile
  private var state: T = initialState

  final override fun getState() = state

  override fun loadState(state: T) {
    this.state = state
  }
}