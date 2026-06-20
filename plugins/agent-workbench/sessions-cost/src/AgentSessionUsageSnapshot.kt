// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.cost

import com.intellij.agent.workbench.common.session.AgentSessionCost
import com.intellij.agent.workbench.common.session.AgentSessionCostKind
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
