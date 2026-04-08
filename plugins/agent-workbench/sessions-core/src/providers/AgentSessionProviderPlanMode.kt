// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.core.providers

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest

const val AGENT_PROMPT_PLAN_MODE_COMMAND: String = "/plan"

fun AgentPromptInitialMessageRequest.isPlanModeRequested(): Boolean {
  return AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in providerOptionIds
}

fun String.isPlanModeCommand(): Boolean {
  if (!startsWith(AGENT_PROMPT_PLAN_MODE_COMMAND)) {
    return false
  }
  val suffix = removePrefix(AGENT_PROMPT_PLAN_MODE_COMMAND)
  return suffix.isEmpty() || suffix.first().isWhitespace()
}

fun String.stripPlanModePrefix(): String {
  if (!isPlanModeCommand()) {
    return this
  }
  return removePrefix(AGENT_PROMPT_PLAN_MODE_COMMAND).trim()
}

fun AgentThreadActivity.isBusyForExistingThreadPlanMode(): Boolean {
  return this == AgentThreadActivity.PROCESSING || this == AgentThreadActivity.REVIEWING
}

fun AgentInitialMessagePlan.isBlockedForExistingThreadPlanMode(threadActivity: AgentThreadActivity?): Boolean {
  return mode == AgentInitialMessageMode.PLAN && threadActivity?.isBusyForExistingThreadPlanMode() == true
}

fun AgentSessionProviderDescriptor.isPlanModeBlockedForExistingThread(
  request: AgentPromptInitialMessageRequest,
  threadActivity: AgentThreadActivity?,
): Boolean {
  return buildInitialMessagePlan(request).isBlockedForExistingThreadPlanMode(threadActivity)
}

fun buildPlanModeInitialMessagePlan(
  request: AgentPromptInitialMessageRequest,
  startupPolicyWhenPlanModeEnabled: AgentInitialMessageStartupPolicy,
  startupPolicyWhenPlanModeDisabled: AgentInitialMessageStartupPolicy = AgentInitialMessageStartupPolicy.TRY_STARTUP_COMMAND,
): AgentInitialMessagePlan {
  val basePlan = AgentInitialMessagePlan.composeDefault(request)
  val normalizedMessage = basePlan.message ?: return basePlan
  val planMode = request.isPlanModeRequested() || normalizedMessage.isPlanModeCommand()
  val message = if (planMode) normalizedMessage.stripPlanModePrefix() else normalizedMessage
  return AgentInitialMessagePlan(
    message = message,
    mode = if (planMode) AgentInitialMessageMode.PLAN else AgentInitialMessageMode.STANDARD,
    startupPolicy = if (planMode) startupPolicyWhenPlanModeEnabled else startupPolicyWhenPlanModeDisabled,
    timeoutPolicy = if (planMode) {
      AgentInitialMessageTimeoutPolicy.REQUIRE_EXPLICIT_READINESS
    }
    else {
      AgentInitialMessageTimeoutPolicy.ALLOW_TIMEOUT_FALLBACK
    },
  )
}
