// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common.session

import java.math.BigDecimal

enum class AgentSessionCostKind {
  EXACT,
  ESTIMATED,
  UNAVAILABLE,
}

data class AgentSessionCost(
  @JvmField val amountUsd: BigDecimal?,
  @JvmField val kind: AgentSessionCostKind,
  @JvmField val matchedModelId: String? = null,
)
