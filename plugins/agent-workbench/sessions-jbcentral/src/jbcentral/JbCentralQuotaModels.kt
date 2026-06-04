// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

internal data class JbCentralQuotaState(
  @JvmField val quotaInfo: JbCentralQuotaInfo? = null,
  @JvmField val error: JbCentralQuotaError? = null,
  @JvmField val isLoading: Boolean = false,
)

internal data class JbCentralQuotaInfo(
  @JvmField val email: String?,
  @JvmField val licenseName: String?,
  @JvmField val usedUsd: String,
  @JvmField val totalUsd: String,
  @JvmField val remainingUsd: String,
  @JvmField val percentUsed: Double?,
  @JvmField val resetDateText: String?,
)

internal enum class JbCentralQuotaError {
  CLI_NOT_FOUND,
  NOT_LOGGED_IN,
  COMMAND_FAILED,
  PARSE_FAILED,
}

internal data class JbCentralQuotaFetchResult(
  @JvmField val quotaInfo: JbCentralQuotaInfo? = null,
  @JvmField val error: JbCentralQuotaError? = null,
)
