// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackendSelector
import com.intellij.agent.workbench.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import com.intellij.agent.workbench.codex.sessions.backend.rollout.CodexRolloutSessionBackend
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CodexSessionBackendSelectorTest {
  @Test
  fun defaultsToAppServerWhenOverrideMissing() {
    val backend = CodexSessionBackendSelector.select(backendOverride = null)

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }

  @Test
  fun usesAppServerWhenOverrideIsAppServer() {
    val backend = CodexSessionBackendSelector.select(backendOverride = "app-server")

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }

  @Test
  fun usesRolloutWhenOverrideIsRollout() {
    val backend = CodexSessionBackendSelector.select(backendOverride = "rollout")

    assertThat(backend).isInstanceOf(CodexRolloutSessionBackend::class.java)
  }

  @Test
  fun fallsBackToAppServerWhenOverrideUnknown() {
    val backend = CodexSessionBackendSelector.select(backendOverride = "unknown-backend")

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }
}
