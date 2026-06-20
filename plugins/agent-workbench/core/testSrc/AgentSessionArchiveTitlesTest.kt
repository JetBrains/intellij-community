// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.core

import com.intellij.agent.workbench.core.session.AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX
import com.intellij.agent.workbench.core.session.buildAgentSessionArchivedThreadTitle
import com.intellij.agent.workbench.core.session.resolveAgentSessionArchivedTitleState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionArchiveTitlesTest {
  @Test
  fun `archived title prefix is stripped from visible title`() {
    val state = resolveAgentSessionArchivedTitleState("${AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX}Fix tests", defaultTitle = "Fallback")

    assertThat(state.archived).isTrue()
    assertThat(state.title).isEqualTo("Fix tests")
  }

  @Test
  fun `missing visible title falls back to default`() {
    val state = resolveAgentSessionArchivedTitleState(AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX, defaultTitle = "Fallback")

    assertThat(state.archived).isTrue()
    assertThat(state.title).isEqualTo("Fallback")
  }

  @Test
  fun `building archived title does not duplicate prefix`() {
    val archivedTitle =
      buildAgentSessionArchivedThreadTitle("${AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX}Fix tests", defaultTitle = "Fallback")

    assertThat(archivedTitle).isEqualTo("${AGENT_SESSION_ARCHIVED_THREAD_TITLE_PREFIX}Fix tests")
  }
}
