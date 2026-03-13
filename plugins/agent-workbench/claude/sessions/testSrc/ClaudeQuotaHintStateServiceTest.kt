// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ClaudeQuotaHintStateServiceTest {
  @Test
  fun defaultsToDisabledAndUnacknowledged() {
    val service = ClaudeQuotaHintStateService()

    assertThat(service.state.eligible).isFalse()
    assertThat(service.state.acknowledged).isFalse()
    assertThat(service.eligibleFlow.value).isFalse()
    assertThat(service.acknowledgedFlow.value).isFalse()
  }

  @Test
  fun stateRoundTrip() {
    val original = ClaudeQuotaHintStateService()
    original.markEligible()
    original.acknowledge()

    val reloaded = ClaudeQuotaHintStateService()
    reloaded.loadState(original.state)

    assertThat(reloaded.state.eligible).isTrue()
    assertThat(reloaded.eligibleFlow.value).isTrue()
    assertThat(reloaded.state.acknowledged).isTrue()
    assertThat(reloaded.acknowledgedFlow.value).isTrue()
  }
}
