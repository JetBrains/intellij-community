// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

abstract class SimplePersistentStateComponent<T : BaseState>(initialState: T) : PersistentStateComponentWithModificationTracker<T> {
  @Volatile
  private var state: T = initialState

  final override fun getState() = state

  final override fun getStateModificationCount() = state.modificationCount

  override fun loadState(state: T) {
    this.state = state
  }
}

@org.jetbrains.annotations.ApiStatus.Experimental
abstract class SerializablePersistentStateComponent<T>(initialState: T) : PersistentStateComponentWithModificationTracker<T> {
  @Volatile
  private var state: T = initialState

  final override fun getState() = state

  override fun loadState(state: T) {
    this.state = state
  }
}