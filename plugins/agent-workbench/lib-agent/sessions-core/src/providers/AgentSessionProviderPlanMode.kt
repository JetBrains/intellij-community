// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.providers

import com.intellij.platform.ai.agent.core.AgentThreadActivity
import com.intellij.agent.workbench.prompt.core.AgentPromptInitialMessageRequest

fun AgentPromptInitialMessageRequest.isPlanModeRequested(): Boolean {
  return AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in providerOptionIds
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
  val planMode = request.isPlanModeRequested()
  val normalizedMessage = basePlan.message
  if (!planMode && normalizedMessage == null) {
    return basePlan
  }
  return AgentInitialMessagePlan(
    message = normalizedMessage,
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
