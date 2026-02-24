// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.claude

internal data class ClaudeQuotaState(
  val quotaInfo: ClaudeQuotaInfo? = null,
  val error: ClaudeQuotaError? = null,
  val isLoading: Boolean = false,
)

internal data class ClaudeQuotaInfo(
  val fiveHourPercent: Int?,
  val fiveHourReset: Long?,
  val sevenDayPercent: Int?,
  val sevenDayReset: Long?,
)

internal enum class ClaudeQuotaError {
  NO_CREDENTIALS,
  AUTH_FAILED,
  NETWORK_ERROR,
  UNKNOWN,
}
