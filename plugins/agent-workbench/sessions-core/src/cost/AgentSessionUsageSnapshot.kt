// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.cost

import java.math.BigDecimal

data class AgentSessionUsageSnapshot(
  @JvmField val modelId: String?,
  @JvmField val inputTokens: Long = 0,
  @JvmField val outputTokens: Long = 0,
  @JvmField val cacheReadTokens: Long = 0,
  @JvmField val cacheWriteTokens: Long = 0,
  @JvmField val requestCount: Long = 0,
  @JvmField val nativeExactCostUsd: BigDecimal? = null,
)
