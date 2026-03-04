// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.common.CodexThread
import com.intellij.agent.workbench.codex.common.CodexThreadActiveFlag
import com.intellij.agent.workbench.codex.common.CodexThreadActivitySnapshot
import com.intellij.agent.workbench.codex.common.CodexThreadStatusKind

internal data class CodexActivitySignals(
  val statusKind: CodexThreadStatusKind,
  val activeFlags: Set<CodexThreadActiveFlag>,
  val hasUnreadAssistantMessage: Boolean,
  val isReviewing: Boolean,
  val hasInProgressTurn: Boolean,
)

internal fun CodexThread.toCodexActivitySignals(): CodexActivitySignals {
  return CodexActivitySignals(
    statusKind = statusKind,
    activeFlags = activeFlags.toSet(),
    hasUnreadAssistantMessage = false,
    isReviewing = false,
    hasInProgressTurn = false,
  )
}

internal fun CodexThreadActivitySnapshot.toCodexActivitySignals(): CodexActivitySignals {
  return CodexActivitySignals(
    statusKind = statusKind,
    activeFlags = activeFlags.toSet(),
    hasUnreadAssistantMessage = hasUnreadAssistantMessage,
    isReviewing = isReviewing,
    hasInProgressTurn = hasInProgressTurn,
  )
}

internal fun resolveCodexSessionActivity(signals: CodexActivitySignals): CodexSessionActivity {
  return when {
    signals.hasUnreadAssistantMessage || CodexThreadActiveFlag.WAITING_ON_USER_INPUT in signals.activeFlags -> CodexSessionActivity.UNREAD
    signals.isReviewing || CodexThreadActiveFlag.WAITING_ON_APPROVAL in signals.activeFlags -> CodexSessionActivity.REVIEWING
    signals.hasInProgressTurn || signals.statusKind == CodexThreadStatusKind.ACTIVE -> CodexSessionActivity.PROCESSING
    else -> CodexSessionActivity.READY
  }
}
