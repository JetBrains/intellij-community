// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import com.intellij.platform.ai.agent.core.session.AgentSessionCost
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageCostCalculator
import com.intellij.platform.ai.agent.sessions.core.cost.AgentSessionUsageSnapshot
import com.intellij.openapi.components.service

internal class LiteLlmAgentSessionUsageCostCalculator : AgentSessionUsageCostCalculator {
  override fun calculateCost(usage: AgentSessionUsageSnapshot): AgentSessionCost {
    return service<LiteLlmPriceCatalogService>().calculateCost(usage)
  }
}
