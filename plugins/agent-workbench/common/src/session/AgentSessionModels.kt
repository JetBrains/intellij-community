// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common.session

// @spec community/plugins/agent-workbench/spec/sessions/agent-terminal-sessions.spec.md

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.common.AgentThreadActivityReport
import com.intellij.openapi.util.NlsSafe

private val AGENT_SESSION_PROVIDER_ID_REGEX = Regex("[a-z][a-z0-9._-]*")

@JvmInline
value class AgentSessionProvider private constructor(val value: String) {
  companion object {
    val CODEX: AgentSessionProvider = from("codex")

    val CLAUDE: AgentSessionProvider = from("claude")

    val JUNIE: AgentSessionProvider = from("junie")

    val PI: AgentSessionProvider = from("pi")

    val TERMINAL: AgentSessionProvider = from("terminal")

    fun from(value: String): AgentSessionProvider {
      require(AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) {
        "Invalid provider id '$value'. Expected: ${AGENT_SESSION_PROVIDER_ID_REGEX.pattern}"
      }
      return AgentSessionProvider(value)
    }

    fun fromOrNull(value: String): AgentSessionProvider? {
      return if (AGENT_SESSION_PROVIDER_ID_REGEX.matches(value)) AgentSessionProvider(value) else null
    }
  }

  override fun toString(): String = value
}

enum class AgentSessionLaunchMode {
  STANDARD,
  YOLO,
}

data class AgentSubAgent(
  @JvmField val id: @NlsSafe String,
  @JvmField val name: @NlsSafe String,
  @JvmField val activity: AgentThreadActivity = AgentThreadActivity.READY,
)

data class AgentSessionThread(
  @JvmField val id: String,
  @JvmField val title: String,
  @JvmField val updatedAt: Long,
  @JvmField val archived: Boolean,
  @JvmField val activityReport: AgentThreadActivityReport = AgentThreadActivityReport.READY,
  val provider: AgentSessionProvider,
  @JvmField val subAgents: List<AgentSubAgent> = emptyList(),
  @JvmField val originBranch: String? = null,
  @JvmField val cost: AgentSessionCost? = null,
) {
  constructor(
    id: String,
    title: String,
    updatedAt: Long,
    archived: Boolean,
    activity: AgentThreadActivity,
    provider: AgentSessionProvider,
    subAgents: List<AgentSubAgent> = emptyList(),
    originBranch: String? = null,
    summaryActivity: AgentThreadActivity? = activity,
    cost: AgentSessionCost? = null,
  ) : this(
    id = id,
    title = title,
    updatedAt = updatedAt,
    archived = archived,
    activityReport = AgentThreadActivityReport(rowActivity = activity, chromeActivity = summaryActivity),
    provider = provider,
    subAgents = subAgents,
    originBranch = originBranch,
    cost = cost,
  )

  val activity: AgentThreadActivity
    get() = activityReport.rowActivity

  val summaryActivity: AgentThreadActivity?
    get() = activityReport.chromeActivity
}
