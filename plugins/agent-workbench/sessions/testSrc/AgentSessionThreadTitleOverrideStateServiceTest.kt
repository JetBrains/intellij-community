// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.state.AgentSessionThreadTitleOverrideStateService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionThreadTitleOverrideStateServiceTest {
  @Test
  fun titleOverrideRoundTripNormalizesPathThreadAndTitle() {
    val service = AgentSessionThreadTitleOverrideStateService()

    assertThat(service.setTitle("/work/project-a/", AgentSessionProvider.CODEX, " thread-1 ", "  Custom\n\n  title  ")).isTrue()

    assertThat(service.getTitle("/work/project-a", AgentSessionProvider.CODEX, "thread-1")).isEqualTo("Custom title")
    assertThat(service.setTitle("/work/project-a", AgentSessionProvider.CODEX, "thread-1", "Custom title")).isFalse()
  }

  @Test
  fun retainPathsRemovesOverridesForUnknownPaths() {
    val service = AgentSessionThreadTitleOverrideStateService()

    service.setTitle("/work/project-a", AgentSessionProvider.CODEX, "thread-a", "Project A title")
    service.setTitle("/work/project-b", AgentSessionProvider.CLAUDE, "thread-b", "Project B title")

    assertThat(service.retainPaths(setOf("/work/project-a/"))).isTrue()

    assertThat(service.getTitle("/work/project-a", AgentSessionProvider.CODEX, "thread-a")).isEqualTo("Project A title")
    assertThat(service.getTitle("/work/project-b", AgentSessionProvider.CLAUDE, "thread-b")).isNull()
    assertThat(service.retainPaths(setOf("/work/project-a"))).isFalse()
  }

  @Test
  fun stateRoundTripPreservesOverrides() {
    val original = AgentSessionThreadTitleOverrideStateService()
    original.setTitle("/work/project-a/", AgentSessionProvider.CODEX, "thread-a", "Project A title")
    original.setTitle("/work/project-a", AgentSessionProvider.CLAUDE, "thread-b", "Project A Claude title")

    val reloaded = AgentSessionThreadTitleOverrideStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.getTitle("/work/project-a", AgentSessionProvider.CODEX, "thread-a")).isEqualTo("Project A title")
    assertThat(reloaded.getTitle("/work/project-a", AgentSessionProvider.CLAUDE, "thread-b")).isEqualTo("Project A Claude title")
  }
}
