// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.common.CodexAppServerStartedThread
import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind

internal fun Collection<CodexThreadActiveFlag>.isResponseRequired(): Boolean {
  return CodexThreadActiveFlag.WAITING_ON_USER_INPUT in this ||
         CodexThreadActiveFlag.WAITING_ON_APPROVAL in this
}

internal fun CodexThread.toCodexSessionActivity(): CodexSessionActivity {
  return resolveCodexSessionActivity(statusKind = statusKind, activeFlags = activeFlags)
}

internal fun CodexAppServerStartedThread.toCodexSessionActivity(): CodexSessionActivity {
  return resolveCodexSessionActivity(statusKind = statusKind, activeFlags = activeFlags)
}

internal fun CodexThreadActivitySnapshot.toCodexSessionActivity(): CodexSessionActivity {
  val effectiveStatusKind = if (hasTurnActivity && !hasInProgressTurn) CodexThreadStatusKind.IDLE else statusKind
  return resolveCodexSessionActivity(
    statusKind = effectiveStatusKind,
    activeFlags = activeFlags,
    hasUnreadAssistantMessage = hasUnreadAssistantMessage,
    hasPendingPlan = hasPendingPlan,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
  )
}

internal fun resolveCodexSessionActivity(
  statusKind: CodexThreadStatusKind,
  activeFlags: Collection<CodexThreadActiveFlag>,
  hasUnreadAssistantMessage: Boolean = false,
  hasPendingPlan: Boolean = false,
  isReviewing: Boolean = false,
  hasInProgressTurn: Boolean = false,
): CodexSessionActivity {
  return when {
    activeFlags.isResponseRequired() || hasPendingPlan -> CodexSessionActivity.NEEDS_INPUT
    isReviewing -> CodexSessionActivity.REVIEWING
    hasInProgressTurn || statusKind == CodexThreadStatusKind.ACTIVE -> CodexSessionActivity.PROCESSING
    hasUnreadAssistantMessage -> CodexSessionActivity.UNREAD
    else -> CodexSessionActivity.READY
  }
}
