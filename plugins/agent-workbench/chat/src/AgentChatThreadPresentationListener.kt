// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.AgentSessionThreadPresentationModel
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Suppress("unused")
@Service(Service.Level.APP)
internal class AgentChatThreadPresentationListener(serviceScope: CoroutineScope) {
  init {
    serviceScope.launch {
      service<AgentSessionThreadPresentationModel>().changes.collect { changeSet ->
        AgentChatOpenTabPresentationInvalidator.invalidate(changeSet)
      }
    }
  }
}

internal class AgentChatThreadPresentationStartupListener : AppLifecycleListener {
  override fun appStarted() {
    service<AgentChatThreadPresentationListener>()
  }
}
