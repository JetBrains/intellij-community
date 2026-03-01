// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend

import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger

private const val BACKEND_OVERRIDE_PROPERTY = "agent.workbench.codex.sessions.backend"
private const val APP_SERVER_BACKEND = "app-server"
private const val ROLLOUT_BACKEND = "rollout"

private val LOG = logger<CodexSessionBackendSelector>()

internal object CodexSessionBackendSelector {
  fun createDefault(): CodexSessionBackend {
    return select(backendOverride = System.getProperty(BACKEND_OVERRIDE_PROPERTY))
  }

  internal fun select(backendOverride: String?): CodexSessionBackend {
    val normalizedOverride = backendOverride
      ?.trim()
      .takeIf { !it.isNullOrEmpty() }
    return when (normalizedOverride) {
      APP_SERVER_BACKEND -> {
        LOG.debug { "Using Codex session backend: $APP_SERVER_BACKEND" }
        CodexAppServerSessionBackend()
      }

      ROLLOUT_BACKEND -> {
        LOG.debug { "Using Codex session backend: $ROLLOUT_BACKEND" }
        CodexRolloutSessionBackend()
      }

      null -> {
        LOG.debug { "Using Codex session backend: $APP_SERVER_BACKEND (default)" }
        CodexAppServerSessionBackend()
      }

      else -> {
        LOG.warn("Unknown Codex session backend '$normalizedOverride', falling back to $APP_SERVER_BACKEND")
        CodexAppServerSessionBackend()
      }
    }
  }
}

fun createDefaultCodexSessionBackend(): CodexSessionBackend {
  return CodexSessionBackendSelector.createDefault()
}
