// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AgentThreadIdentityTest {
  @Test
  fun buildsAndParsesValidIdentity() {
    val identity = buildAgentThreadIdentity(providerId = "codex", threadId = "thread-1")

    assertThat(identity).isEqualTo("codex:thread-1")
    assertThat(parseAgentThreadIdentity(identity)).isEqualTo(AgentThreadIdentity(providerId = "codex", threadId = "thread-1"))
  }

  @Test
  fun rejectsInvalidProviderIdWhenBuildingIdentity() {
    assertThatThrownBy { buildAgentThreadIdentity(providerId = "Codex", threadId = "thread-1") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Invalid provider id")
  }

  @Test
  fun rejectsBlankThreadIdWhenBuildingIdentity() {
    assertThatThrownBy { buildAgentThreadIdentity(providerId = "codex", threadId = " ") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("Thread id must not be blank")
  }

  @Test
  fun rejectsMalformedIdentityWhenParsing() {
    assertThat(parseAgentThreadIdentity("codex")).isNull()
    assertThat(parseAgentThreadIdentity("Codex:thread-1")).isNull()
    assertThat(parseAgentThreadIdentity("codex: ")).isNull()
  }
}
