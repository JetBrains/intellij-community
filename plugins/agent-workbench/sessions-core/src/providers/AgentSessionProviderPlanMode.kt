// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInitialMessageRequest

const val AGENT_PROMPT_PLAN_MODE_COMMAND: String = "/plan"

fun AgentPromptInitialMessageRequest.isPlanModeRequested(): Boolean {
  return planModeEnabled || AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in providerOptionIds
}

fun String.isPlanModeCommand(): Boolean {
  if (!startsWith(AGENT_PROMPT_PLAN_MODE_COMMAND)) {
    return false
  }
  val suffix = removePrefix(AGENT_PROMPT_PLAN_MODE_COMMAND)
  return suffix.isEmpty() || suffix.first().isWhitespace()
}

fun String.ensurePlanModePrefix(): String {
  val normalized = trim()
  if (normalized.isEmpty()) {
    return AGENT_PROMPT_PLAN_MODE_COMMAND
  }
  if (normalized.isPlanModeCommand()) {
    return normalized
  }
  return "$AGENT_PROMPT_PLAN_MODE_COMMAND $normalized"
}

fun String.stripPlanModePrefix(): String {
  if (!isPlanModeCommand()) {
    return this
  }
  return removePrefix(AGENT_PROMPT_PLAN_MODE_COMMAND).trim()
}

fun buildPlanModeInitialMessagePlan(
  request: AgentPromptInitialMessageRequest,
  startupPolicyWhenPlanModeEnabled: AgentInitialMessageStartupPolicy,
  startupPolicyWhenPlanModeDisabled: AgentInitialMessageStartupPolicy = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
): AgentInitialMessagePlan {
  val basePlan = AgentInitialMessagePlan.composeDefault(request)
  val normalizedMessage = basePlan.message ?: return basePlan
  val planModeRequested = request.isPlanModeRequested()
  val message = if (planModeRequested) normalizedMessage.ensurePlanModePrefix() else normalizedMessage
  return AgentInitialMessagePlan(
    message = message,
    startupPolicy = if (planModeRequested) startupPolicyWhenPlanModeEnabled else startupPolicyWhenPlanModeDisabled,
    timeoutPolicy = if (message.isPlanModeCommand()) {
      AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS
    }
    else {
      AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
    },
  )
}
