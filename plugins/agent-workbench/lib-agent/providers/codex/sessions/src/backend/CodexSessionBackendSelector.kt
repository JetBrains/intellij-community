// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.sessions.CodexThreadPathIndex
import com.intellij.agent.workbench.codex.sessions.CodexThreadPathIndexService
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

private const val APP_SERVER_BACKEND = "app-server"

private val LOG = logger<CodexSessionBackendSelector>()

internal object CodexSessionBackendSelector {
  fun createDefault(threadPathIndex: CodexThreadPathIndex = service<CodexThreadPathIndexService>()): CodexSessionBackend {
    LOG.debug { "Using Codex session backend: $APP_SERVER_BACKEND (default)" }
    return CodexAppServerSessionBackend(threadPathIndex = threadPathIndex)
  }

  internal fun select(
    backendOverride: String?,
    threadPathIndex: CodexThreadPathIndex = service<CodexThreadPathIndexService>(),
  ): CodexSessionBackend {
    if (!backendOverride.isNullOrBlank()) {
      LOG.debug {
        "Ignoring Codex session backend override '$backendOverride'; using $APP_SERVER_BACKEND"
      }
    }
    return CodexAppServerSessionBackend(threadPathIndex = threadPathIndex)
  }
}

internal fun createDefaultCodexSessionBackend(
  threadPathIndex: CodexThreadPathIndex = service<CodexThreadPathIndexService>(),
): CodexSessionBackend {
  return CodexSessionBackendSelector.createDefault(threadPathIndex = threadPathIndex)
}
