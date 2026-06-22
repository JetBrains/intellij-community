// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider

interface AgentOpenTopLevelThreadDispatchService {
  suspend fun dispatchIfPresent(
    projectPath: String,
    provider: AgentSessionProvider,
    threadId: String,
    launchSpec: AgentSessionTerminalLaunchSpec,
    initialMessageDispatchPlan: AgentInitialMessageDispatchPlan,
  ): Boolean
}
