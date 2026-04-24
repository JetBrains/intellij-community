// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.codex

internal enum class AgentChatOpenRoute {
  DedicatedFrame,
  CurrentProject,
  OpenSourceProject,
}

internal fun resolveAgentChatOpenRoute(
  openInDedicatedFrame: Boolean,
  hasOpenSourceProject: Boolean,
): AgentChatOpenRoute {
  if (openInDedicatedFrame) return AgentChatOpenRoute.DedicatedFrame
  if (hasOpenSourceProject) return AgentChatOpenRoute.CurrentProject
  return AgentChatOpenRoute.OpenSourceProject
}
