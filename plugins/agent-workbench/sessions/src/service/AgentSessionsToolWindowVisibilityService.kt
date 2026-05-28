// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.openapi.components.Service
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Service(Service.Level.APP)
class AgentSessionsToolWindowVisibilityService {
  private val lock = Any()
  private val visibleTokens = LinkedHashSet<String>()
  private val _visibleFlow = MutableStateFlow(false)

  val visibleFlow: StateFlow<Boolean> = _visibleFlow.asStateFlow()

  fun setVisible(token: String, visible: Boolean) {
    synchronized(lock) {
      val changed = if (visible) visibleTokens.add(token) else visibleTokens.remove(token)
      if (changed) {
        _visibleFlow.value = visibleTokens.isNotEmpty()
      }
    }
  }

  fun release(token: String) {
    setVisible(token = token, visible = false)
  }
}
