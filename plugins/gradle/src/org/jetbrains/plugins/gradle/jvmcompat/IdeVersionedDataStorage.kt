// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.jvmcompat

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.components.PersistentStateComponent

abstract class IdeVersionedDataStorage<S: IdeVersionedDataState>(
  private val parser: IdeVersionedDataParser<S>,
  private val defaultState: S,
) : PersistentStateComponent<S?> {

  protected abstract fun newState(): S

  private fun initializeDefaultState(): S {
    val newState = newState()
    newState.copyFrom(defaultState)
    newState.ideVersion = ApplicationInfo.getInstance().fullVersion
    newState.isDefault = true
    return newState
  }

  @Volatile
  private var currentState: S = initializeDefaultState()

  protected open fun onStateChanged(newState: S) {
    // Override to change state in any subclass
  }


  override fun getState(): S? {
    return currentState
  }

  override fun loadState(state: S) {
    currentState = if (state.isDefault || state.ideVersion != ApplicationInfo.getInstance().fullVersion) {
      initializeDefaultState()
    } else state
    onStateChanged(currentState)
  }

  fun setStateAsString(json: String) {
    parser.parseVersionedJson(json, ApplicationInfo.getInstance().fullVersion)?.let {
      currentState = it
      onStateChanged(currentState)
    }
  }

  override fun noStateLoaded() {
    initializeDefaultState()
    onStateChanged(currentState)
  }
}