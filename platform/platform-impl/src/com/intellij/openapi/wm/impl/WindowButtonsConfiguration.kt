// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.openapi.wm.impl

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Cache state for quick application start-up
 */
@ApiStatus.Internal
@State(name = "WindowButtonsConfiguration", storages = [Storage(StoragePathMacros.CACHE_FILE)])
class WindowButtonsConfiguration(scope: CoroutineScope) : PersistentStateComponent<WindowButtonsConfiguration.State?> {
  enum class WindowButton {
    MINIMIZE,
    MAXIMIZE,
    CLOSE
  }

  companion object {
    fun getInstance(): WindowButtonsConfiguration? = if (isSupported()) service<WindowButtonsConfiguration>() else null

    private val log = thisLogger()
    private const val WINDOW_BUTTONS_CONFIG_KEY = "ide.linux.window.buttons.config"

    private fun isSupported(): Boolean {
      return SystemInfoRt.isLinux
    }
  }

  private var mutableStateFlow = MutableStateFlow<State?>(null)
  val stateFlow: StateFlow<State?> = mutableStateFlow.asStateFlow()

  init {
    if (isSupported()) {
      scope.launch(CoroutineName("WindowButtonsConfiguration")) {
        val registryManager = RegistryManager.getInstanceAsync()

        var state = loadStateFromRegistry(registryManager) ?: loadStateFromOs()
        mutableStateFlow.update { state }
      }
    }
  }

  override fun getState(): State? {
    return mutableStateFlow.value
  }

  override fun loadState(state: State) {
    mutableStateFlow.update { currentState ->
      // The saved state is used only for caching, the actual computed state takes priority.
      // Normally the state isn't computed yet, unless loadState is called when the service is already up and running.
      // This is possible, according to the API docs, but only if the files are modified when the IDE is running.
      // In this case we definitely don't want to pick up the modified value, as the sources of truth are the OS and the registry.
      currentState ?: state
    }
  }

  private fun loadStateFromRegistry(registryManager: RegistryManager): State? {
    val value = registryManager.stringValue(WINDOW_BUTTONS_CONFIG_KEY)
    if (value.isNullOrEmpty()) {
      return null
    }

    val result = parseFromString(value)
    if (result == null) {
      log.warn("Failed to parse '$WINDOW_BUTTONS_CONFIG_KEY' registry value: $value")
    }

    return result
  }

  private fun loadStateFromOs(): State? {
    ThreadingAssertions.assertBackgroundThread()

    val config = X11UiUtil.getWindowButtonsConfig() ?: return null
    val result = parseFromString(config)
    if (result == null) {
      log.warn("Failed to parse OS window buttons config: $config")
    }
    return result
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
