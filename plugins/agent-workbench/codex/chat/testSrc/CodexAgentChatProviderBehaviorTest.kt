// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

import com.intellij.agent.workbench.chat.AgentChatBehaviorFile
import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentInitialMessageMode
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class CodexAgentChatProviderBehaviorTest {
  private val behavior = CodexAgentChatProviderBehavior()

  @Test
  fun concreteThreadRebindCommandsAreExactNewOrFork() {
    assertThat(behavior.isConcreteNewThreadRebindCommand("/new")).isTrue()
    assertThat(behavior.isConcreteNewThreadRebindCommand("/fork")).isTrue()

    assertThat(behavior.isConcreteNewThreadRebindCommand("/fork now")).isFalse()
    assertThat(behavior.isConcreteNewThreadRebindCommand("/forkx")).isFalse()
    assertThat(behavior.isConcreteNewThreadRebindCommand("echo /fork")).isFalse()
  }

  @Test
  fun pendingThreadRefreshRetryIsDisabled() {
    val file = object : AgentChatBehaviorFile {
      override val provider: AgentSessionProvider = AgentSessionProvider.from("codex")
      override val isPendingThread: Boolean = true
      override val subAgentId: String? = null
      override val pendingFirstInputAtMs: Long = 1_000L
      override val threadActivity: AgentThreadActivity = AgentThreadActivity.READY
      override val initialMessageMode: AgentInitialMessageMode? = null
    }

    assertThat(behavior.supportsPendingThreadRefreshRetry(file)).isFalse()
    assertThat(behavior.pendingThreadRefreshRetryDelayMs(file, currentTimeMs = 1_000L, retryIntervalMs = 100L)).isNull()
  }
}
