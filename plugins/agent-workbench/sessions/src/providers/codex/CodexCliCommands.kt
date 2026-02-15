// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.providers.codex

import com.intellij.agent.workbench.codex.common.CodexCliUtils

internal object CodexCliCommands {
  fun buildNewSessionCommand(yolo: Boolean): List<String> =
    if (yolo) listOf(CodexCliUtils.CODEX_COMMAND, "--full-auto")
    else listOf(CodexCliUtils.CODEX_COMMAND)

  fun buildResumeCommand(sessionId: String): List<String> =
    listOf(CodexCliUtils.CODEX_COMMAND, "resume", sessionId)
}
