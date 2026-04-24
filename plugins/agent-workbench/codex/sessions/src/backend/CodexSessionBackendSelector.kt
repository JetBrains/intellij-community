// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

private const val APP_SERVER_BACKEND = "app-server"

private val LOG = logger<CodexSessionBackendSelector>()

internal object CodexSessionBackendSelector {
  fun createDefault(): CodexSessionBackend {
    LOG.debug { "Using Codex session backend: $APP_SERVER_BACKEND (default)" }
    return CodexAppServerSessionBackend()
  }

  internal fun select(backendOverride: String?): CodexSessionBackend {
    if (!backendOverride.isNullOrBlank()) {
      LOG.debug {
        "Ignoring Codex session backend override '$backendOverride'; using $APP_SERVER_BACKEND"
      }
    }
    return CodexAppServerSessionBackend()
  }
}

fun createDefaultCodexSessionBackend(): CodexSessionBackend {
  return CodexSessionBackendSelector.createDefault()
}
