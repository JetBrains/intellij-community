// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.openapi.wm.impl

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Cache state for quick application start-up
 */
@ApiStatus.Internal
@State(name = "WindowButtonsConfiguration", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class WindowButtonsConfiguration(private val scope: CoroutineScope) : PersistentStateComponent<WindowButtonsConfiguration.State?> {

  enum class WindowButton {
    MINIMIZE,
    MAXIMIZE,
    CLOSE
  }

  companion object {
    fun getInstance(): WindowButtonsConfiguration? = if (isSupported()) service<WindowButtonsConfiguration>() else null

    private val log = thisLogger()

    private fun isSupported(): Boolean {
      return SystemInfoRt.isLinux
    }
  }

  private var mutableStateFlow = MutableStateFlow<State?>(null)

  @Volatile
  private var userCustomizedState: State? = null

  val stateFlow: StateFlow<State?> = mutableStateFlow.asStateFlow()

  override fun getState(): State? {
    return mutableStateFlow.value
  }

  override fun loadState(state: State) {
    mutableStateFlow.value = state
    scheduleUpdateFromOs()
  }

  override fun noStateLoaded() {
    scheduleUpdateFromOs()
  }

  fun setCustomizedState(state: String?) {
    val newState: State?
    if (state.isNullOrEmpty()) {
      newState = null
    }
    else {
      newState = parseFromString(state)
      if (newState == null) {
        log.warn("Failed to parse custom user window buttons config: $state")
      }
    }

    if (userCustomizedState == null && newState == null) {
      // Avoid unnecessary X11UiUtil.getWindowButtonsConfig invocation
      return
    }

    userCustomizedState = newState
    scheduleUpdateFromOs()
  }

  private fun scheduleUpdateFromOs() {
    scope.launch {
      loadStateFromOs()
    }
  }

  private fun loadStateFromOs() {
    if (!isSupported()) {
      return
    }

    ThreadingAssertions.assertBackgroundThread()

    var windowButtonsState: State? = userCustomizedState

    if (windowButtonsState == null) {
      val config = X11UiUtil.getWindowButtonsConfig()
      if (config != null) {
        windowButtonsState = parseFromString(config)

        if (windowButtonsState == null) {
          log.warn("Failed to parse OS window buttons config: $config")
        }
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
    WindowButtonsConfiguration.getInstance()?.setCustomizedState(Registry.stringValue("ide.linux.window.buttons.config"))
  }
}