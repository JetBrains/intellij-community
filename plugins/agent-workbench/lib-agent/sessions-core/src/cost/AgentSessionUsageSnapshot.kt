// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import com.intellij.agent.workbench.core.session.AgentSessionCost
import com.intellij.agent.workbench.core.session.AgentSessionCostKind
import com.intellij.openapi.extensions.ExtensionPointName
import java.math.BigDecimal

data class AgentSessionUsageSnapshot(
  @JvmField val modelId: String?,
  @JvmField val inputTokens: Long = 0,
  @JvmField val outputTokens: Long = 0,
  @JvmField val cacheReadTokens: Long = 0,
  @JvmField val cacheWriteTokens: Long = 0,
  @JvmField val cacheWrite5mTokens: Long = 0,
  @JvmField val cacheWrite1hTokens: Long = 0,
  @JvmField val reasoningTokens: Long = 0,
  @JvmField val requestCount: Long = 0,
  @JvmField val nativeExactCostUsd: BigDecimal? = null,
)

fun List<AgentSessionUsageSnapshot>.aggregateAgentSessionUsageCost(
  calculateCost: (AgentSessionUsageSnapshot) -> AgentSessionCost,
): AgentSessionCost? {
  if (isEmpty()) return null

  val componentCosts = map(calculateCost)
  if (componentCosts.any { it.amountUsd == null }) {
    return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
  }

  val totalAmount = componentCosts.fold(BigDecimal.ZERO) { acc, cost ->
    acc + checkNotNull(cost.amountUsd)
  }
  val kind = if (componentCosts.all { it.kind == AgentSessionCostKind.EXACT }) {
    AgentSessionCostKind.EXACT
  }
  else {
    AgentSessionCostKind.ESTIMATED
  }
  val matchedModelId = componentCosts.mapNotNull(AgentSessionCost::matchedModelId).distinct().singleOrNull()
  return AgentSessionCost(amountUsd = totalAmount, kind = kind, matchedModelId = matchedModelId)
}

interface AgentSessionUsageCostCalculator {
  fun calculateCost(usage: AgentSessionUsageSnapshot): AgentSessionCost
}

object AgentSessionUsageCostCalculators {
  val EP_NAME: ExtensionPointName<AgentSessionUsageCostCalculator> =
    ExtensionPointName("com.intellij.agent.workbench.sessionUsageCostCalculator")

  fun calculateCost(usage: AgentSessionUsageSnapshot): AgentSessionCost {
    val calculator = EP_NAME.extensionList.firstOrNull()
    if (calculator != null) {
      return calculator.calculateCost(usage)
    }
    usage.nativeExactCostUsd?.let { exactCost ->
      return AgentSessionCost(amountUsd = exactCost, kind = AgentSessionCostKind.EXACT)
    }
    return AgentSessionCost(amountUsd = null, kind = AgentSessionCostKind.UNAVAILABLE)
  }
}
