// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.chat.AgentChatTerminalTitleThreadRebindContributors
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexTerminalTitleThreadRebindContributorTest {
  private val contributor = CodexTerminalTitleThreadRebindContributor()

  @Test
  fun extractsFullThreadIdFromTerminalTitle() {
    val threadId = "018f4b30-f1b2-7000-9b4d-abcdef123456"

    assertThat(contributor.extractThreadId("Codex · $threadId · /work/project-a"))
      .isEqualTo(threadId)
    assertThat(contributor.extractThreadId("CODEX · 018F4B30-F1B2-7000-9B4D-ABCDEF123456"))
      .isEqualTo(threadId)
    assertThat(contributor.extractThreadId("$threadId | Fix indexing bug | /work/project-a"))
      .isEqualTo(threadId)
    assertThat(contributor.extractThreadId("Codex · Fix indexing bug · /work/project-a"))
      .isNull()
  }

  @Test
  fun contributorIsRegistered() {
    assertThat(AgentChatTerminalTitleThreadRebindContributors.find(AgentSessionProvider.CODEX))
      .isInstanceOf(CodexTerminalTitleThreadRebindContributor::class.java)
  }
}
