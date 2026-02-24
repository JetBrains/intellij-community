// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AgentSessionsSwingQuotaHintTest {
  @Test
  fun hintVisibilityRequiresEligibleUnacknowledgedAndDisabledWidget() {
    assertTrue(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false))
    assertFalse(shouldShowClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = false))
    assertFalse(shouldShowClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = false))
    assertFalse(shouldShowClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true))
  }

  @Test
  fun acknowledgementRequiresEligibleUnacknowledgedAndEnabledWidget() {
    assertTrue(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = true))
    assertFalse(shouldAcknowledgeClaudeQuotaHint(eligible = false, acknowledged = false, widgetEnabled = true))
    assertFalse(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = true, widgetEnabled = true))
    assertFalse(shouldAcknowledgeClaudeQuotaHint(eligible = true, acknowledged = false, widgetEnabled = false))
  }
}
