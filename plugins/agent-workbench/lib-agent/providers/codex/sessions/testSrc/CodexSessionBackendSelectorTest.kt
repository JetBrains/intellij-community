// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.codex.sessions

import com.intellij.platform.ai.agent.codex.sessions.backend.CodexSessionBackendSelector
import com.intellij.platform.ai.agent.codex.sessions.backend.appserver.CodexAppServerSessionBackend
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexSessionBackendSelectorTest {
  @Test
  fun defaultsToAppServerWhenOverrideMissing() {
    val backend = CodexSessionBackendSelector.select(
      backendOverride = null,
      threadPathIndex = InMemoryCodexThreadPathIndex(),
    )

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }

  @Test
  fun usesAppServerWhenOverrideIsAppServer() {
    val backend = CodexSessionBackendSelector.select(
      backendOverride = "app-server",
      threadPathIndex = InMemoryCodexThreadPathIndex(),
    )

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }

  @Test
  fun keepsUsingAppServerWhenOverrideIsRollout() {
    val backend = CodexSessionBackendSelector.select(
      backendOverride = "rollout",
      threadPathIndex = InMemoryCodexThreadPathIndex(),
    )

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }

  @Test
  fun fallsBackToAppServerWhenOverrideUnknown() {
    val backend = CodexSessionBackendSelector.select(
      backendOverride = "unknown-backend",
      threadPathIndex = InMemoryCodexThreadPathIndex(),
    )

    assertThat(backend).isInstanceOf(CodexAppServerSessionBackend::class.java)
  }
}
