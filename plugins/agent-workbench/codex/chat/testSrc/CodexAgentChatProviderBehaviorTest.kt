// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.chat

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
}
