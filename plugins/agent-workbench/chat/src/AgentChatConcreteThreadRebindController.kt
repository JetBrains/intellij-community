// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

internal class AgentChatConcreteThreadRebindController(
  private val file: AgentChatVirtualFile,
  private val behavior: AgentChatProviderBehavior,
  private val tabSnapshotWriter: AgentChatTabSnapshotWriter,
  private val currentTimeProvider: () -> Long = System::currentTimeMillis,
) : AgentChatDisposableController {
  private var rebindJob: Job? = null

  fun attach(tab: AgentChatTerminalTab, descriptor: AgentSessionProviderDescriptor?) {
    val provider = file.provider ?: return
    if (!behavior.supportsConcreteNewThreadRebind(file, descriptor)) {
      return
    }
    rebindJob = tab.coroutineScope.launch {
      val commandTracker = AgentChatTerminalCommandTracker()
      tab.keyEventsFlow.collectLatest { event ->
        val executedCommand = commandTracker.record(event.awtEvent) ?: return@collectLatest
        if (!behavior.isConcreteNewThreadRebindCommand(executedCommand)) {
          return@collectLatest
        }
        if (!file.updateNewThreadRebindRequestedAtMs(currentTimeProvider())) {
          return@collectLatest
        }
        tabSnapshotWriter.upsert(file.toSnapshot())
        notifyAgentChatTerminalOutputForRefresh(provider = provider, projectPath = file.projectPath)
      }
    }
  }

  override fun dispose() {
    rebindJob?.cancel()
    rebindJob = null
  }
}
