// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class ValueLookupManagerController {
  private val listeningFlow = MutableStateFlow(false)

  /**
   * Starts [ValueLookupManager] listening for events (e.g. mouse movement) to trigger evaluation popups
   */
  fun startListening() {
    listeningFlow.value = true
  }

  fun getListeningStateFlow(): StateFlow<Boolean> = listeningFlow.asStateFlow()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ValueLookupManagerController = project.service<ValueLookupManagerController>()
  }
}