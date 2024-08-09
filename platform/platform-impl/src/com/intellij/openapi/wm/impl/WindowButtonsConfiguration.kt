// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.*
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Cache state for quick application start-up
 */
@State(name = "WindowButtonsConfiguration", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class WindowButtonsConfiguration(private val scope: CoroutineScope) : PersistentStateComponent<WindowButtonsConfiguration.State?> {

  enum class WindowButton {
    MINIMIZE,
    MAXIMIZE,
    CLOSE
  }

  companion object {
    fun getInstance(): WindowButtonsConfiguration? = if (isSupported()) service<WindowButtonsConfiguration>() else null

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
    scheduleUpdateFromOs()
  }

  override fun noStateLoaded() {
    // Load state and wait the result
    loadStateFromOs()
  }

  fun scheduleUpdateFromOs() {
    scope.launch {
      loadStateFromOs()
    }
  }
  private fun loadStateFromOs() {
    var windowButtonsState: State? = null

    if (isSupported()) {
      val customConfig = if (LoadingState.COMPONENTS_LOADED.isOccurred) Registry.stringValue("ide.linux.window.buttons.config") else ""
      val config = customConfig.ifBlank { X11UiUtil.getWindowButtonsConfig() }
      if (config != null) {
        windowButtonsState = parseFromString(config)
      }
    }
    mutableStateFlow.value = windowButtonsState
  }

  class State {
    @JvmField
    var rightPosition: Boolean = true

    @JvmField
    var buttons: List<WindowButton> = emptyList()
  }
}

private fun parseFromString(s: String): WindowButtonsConfiguration.State? {
  val iconAndButtons = s.split(":")
  if (iconAndButtons.size != 2) {
    return null
  }

  val leftIcons = stringsToWindowButtons(iconAndButtons[0].split(","))
  val rightIcons = stringsToWindowButtons(iconAndButtons[1].split(","))
  val buttons = leftIcons + rightIcons

  // Check on duplicate icons
  for (button in buttons) {
    if (buttons.count { it == button } != 1) {
      return null
    }
  }

  return WindowButtonsConfiguration.State().apply {
    this.rightPosition = leftIcons.isEmpty() || rightIcons.isNotEmpty()
    this.buttons = buttons
  }
}

private fun stringsToWindowButtons(strings: List<String>): List<WindowButtonsConfiguration.WindowButton> {
  return strings.mapNotNull {
    when (it) {
      "minimize" -> WindowButtonsConfiguration.WindowButton.MINIMIZE
      "maximize" -> WindowButtonsConfiguration.WindowButton.MAXIMIZE
      "close" -> WindowButtonsConfiguration.WindowButton.CLOSE
      else -> null
    }
  }
}

internal class WindowButtonsAppLifecycleListener : AppLifecycleListener {

  override fun appStarted() {
    WindowButtonsConfiguration.getInstance()?.scheduleUpdateFromOs()
  }
}