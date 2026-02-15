// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

private const val BACKEND_OVERRIDE_PROPERTY = "agent.workbench.codex.sessions.backend"
private const val APP_SERVER_BACKEND = "app-server"

fun createDefaultCodexSessionBackend(): CodexSessionBackend {
  return when (System.getProperty(BACKEND_OVERRIDE_PROPERTY)) {
    APP_SERVER_BACKEND -> CodexAppServerSessionBackend()
    else -> CodexRolloutSessionBackend()
  }
}

