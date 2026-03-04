// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.common.AgentThreadActivity

internal fun CodexSessionActivity.toAgentThreadActivity(): AgentThreadActivity {
  return when (this) {
    CodexSessionActivity.UNREAD -> AgentThreadActivity.UNREAD
    CodexSessionActivity.REVIEWING -> AgentThreadActivity.REVIEWING
    CodexSessionActivity.PROCESSING -> AgentThreadActivity.PROCESSING
    CodexSessionActivity.READY -> AgentThreadActivity.READY
  }
}

