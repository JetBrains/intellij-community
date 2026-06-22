// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.common

const val CLAUDE_USER_INTERACTION_TOOL_MATCHER: String = "AskUserQuestion|ExitPlanMode"
const val CLAUDE_HOOK_PROJECT_MUTATING_TOOL_MATCHER: String = "Write|Edit|MultiEdit|NotebookEdit"

fun isClaudeUserInteractionToolName(toolName: String?): Boolean {
  return when (toolName?.trim()) {
    "AskUserQuestion", "ExitPlanMode" -> true
    else -> false
  }
}

fun isClaudeHookProjectMutatingToolName(toolName: String?): Boolean {
  return when (toolName?.trim()) {
    "Write", "Edit", "MultiEdit", "NotebookEdit" -> true
    else -> false
  }
}

fun isClaudeTranscriptProjectMutatingToolName(toolName: String?): Boolean {
  return when (toolName?.trim()?.lowercase()) {
    "bash", "edit", "multiedit", "write", "notebookedit" -> true
    else -> false
  }
}
