// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.frameButtons

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.wm.impl.X11UiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Cache state for quick application start-up
 */
@State(name = "LinuxIconThemeConfiguration", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class LinuxIconThemeConfiguration(private val scope: CoroutineScope) : PersistentStateComponent<LinuxIconThemeConfiguration.State?> {

  companion object {
    fun getInstance(): LinuxIconThemeConfiguration? = if (isSupported()) service<LinuxIconThemeConfiguration>() else null

    private fun isSupported(): Boolean {
      return SystemInfoRt.isLinux && X11UiUtil.isInitialized()
    }
  }

  private var mutableStateFlow = MutableStateFlow<State?>(null)
  val stateFlow = mutableStateFlow.asStateFlow()

  override fun getState(): State? {
    return mutableStateFlow.value
  }

  override fun loadState(state: State) {
    mutableStateFlow.value = state

    scope.launch {
      loadStateFromOs()
    }
  }

  override fun noStateLoaded() {
    // Load state and wait the result
    loadStateFromOs()
  }

  private fun loadStateFromOs() {
    val iconTheme = if (isSupported()) X11UiUtil.getIconTheme() else null

    mutableStateFlow.value = State().apply {
      this.iconTheme = iconTheme
    }
  }

  class State {
    @JvmField
    var iconTheme: String? = null
  }
}
