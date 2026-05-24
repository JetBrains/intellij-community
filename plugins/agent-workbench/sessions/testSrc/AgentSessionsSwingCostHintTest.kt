// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionsSwingCostHintTest {
  @Test
  fun hintVisibilityRequiresEligibleUnacknowledgedAndDisabledSetting() {
    assertThat(shouldShowAgentSessionCostHint(eligible = true, acknowledged = false, settingEnabled = false)).isTrue()
    assertThat(shouldShowAgentSessionCostHint(eligible = false, acknowledged = false, settingEnabled = false)).isFalse()
    assertThat(shouldShowAgentSessionCostHint(eligible = true, acknowledged = true, settingEnabled = false)).isFalse()
    assertThat(shouldShowAgentSessionCostHint(eligible = true, acknowledged = false, settingEnabled = true)).isFalse()
  }

  @Test
  fun acknowledgementRequiresEligibleUnacknowledgedAndEnabledSetting() {
    assertThat(shouldAcknowledgeAgentSessionCostHint(eligible = true, acknowledged = false, settingEnabled = true)).isTrue()
    assertThat(shouldAcknowledgeAgentSessionCostHint(eligible = false, acknowledged = false, settingEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeAgentSessionCostHint(eligible = true, acknowledged = true, settingEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeAgentSessionCostHint(eligible = true, acknowledged = false, settingEnabled = false)).isFalse()
  }
}
