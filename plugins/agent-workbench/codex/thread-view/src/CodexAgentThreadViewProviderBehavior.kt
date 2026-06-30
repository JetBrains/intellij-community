// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.thread.view

import com.intellij.agent.workbench.thread.view.AgentThreadViewBehaviorFile
import com.intellij.agent.workbench.thread.view.AgentThreadViewProviderBehavior
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviderDescriptor

internal class CodexAgentThreadViewProviderBehavior : AgentThreadViewProviderBehavior {
  override fun supportsConcreteNewThreadRebind(
    file: AgentThreadViewBehaviorFile,
    descriptor: AgentSessionProviderDescriptor?,
  ): Boolean {
    return descriptor?.supportsNewThreadRebind == true && !file.isPendingThread && file.subAgentId == null
  }

  override fun isConcreteNewThreadRebindCommand(command: String): Boolean {
    return command == CODEX_NEW_THREAD_COMMAND || command == CODEX_FORK_THREAD_COMMAND
  }
}

private const val CODEX_NEW_THREAD_COMMAND: String = "/new"
private const val CODEX_FORK_THREAD_COMMAND: String = "/fork"
