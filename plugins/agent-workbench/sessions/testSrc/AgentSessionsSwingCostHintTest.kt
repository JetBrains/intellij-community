// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.ui.shouldAcknowledgeAgentWorkbenchHintBanner
import com.intellij.agent.workbench.ui.shouldShowAgentWorkbenchHintBanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentSessionsSwingCostHintTest {
  @Test
  fun hintVisibilityRequiresEligibleUnacknowledgedAndDisabledSetting() {
    assertThat(shouldShowAgentWorkbenchHintBanner(eligible = true, acknowledged = false, featureEnabled = false)).isTrue()
    assertThat(shouldShowAgentWorkbenchHintBanner(eligible = false, acknowledged = false, featureEnabled = false)).isFalse()
    assertThat(shouldShowAgentWorkbenchHintBanner(eligible = true, acknowledged = true, featureEnabled = false)).isFalse()
    assertThat(shouldShowAgentWorkbenchHintBanner(eligible = true, acknowledged = false, featureEnabled = true)).isFalse()
  }

  @Test
  fun acknowledgementRequiresEligibleUnacknowledgedAndEnabledSetting() {
    assertThat(shouldAcknowledgeAgentWorkbenchHintBanner(eligible = true, acknowledged = false, featureEnabled = true)).isTrue()
    assertThat(shouldAcknowledgeAgentWorkbenchHintBanner(eligible = false, acknowledged = false, featureEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeAgentWorkbenchHintBanner(eligible = true, acknowledged = true, featureEnabled = true)).isFalse()
    assertThat(shouldAcknowledgeAgentWorkbenchHintBanner(eligible = true, acknowledged = false, featureEnabled = false)).isFalse()
  }
}
