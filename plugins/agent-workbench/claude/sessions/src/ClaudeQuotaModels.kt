// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

internal data class ClaudeQuotaState(
  @JvmField val quotaInfo: ClaudeQuotaInfo? = null,
  @JvmField val error: ClaudeQuotaError? = null,
  @JvmField val isLoading: Boolean = false,
)

internal data class ClaudeQuotaInfo(
  @JvmField val fiveHourPercent: Int?,
  @JvmField val fiveHourReset: Long?,
  @JvmField val sevenDayPercent: Int?,
  @JvmField val sevenDayReset: Long?,
)

internal enum class ClaudeQuotaError {
  NO_CREDENTIALS,
  AUTH_FAILED,
  NETWORK_ERROR,
  UNKNOWN,
}
