// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.ui.shouldAcknowledgeClaudeQuotaHint
import com.intellij.agent.workbench.sessions.ui.shouldShowClaudeQuotaHint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionsSwingQuotaHintTest {
  @Test
  fun hintVisibilityRequiresEligibleUnacknowledgedAndDisabledWidget() {
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false)).isTrue()
    assertThat(shouldShowClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = false)).isFalse()
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = false)).isFalse()
    assertThat(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true)).isFalse()
  }

  @Test
  fun acknowledgementRequiresEligibleUnacknowledgedAndEnabledWidget() {
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true)).isTrue()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false)).isFalse()
  }
}
