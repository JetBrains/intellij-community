// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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