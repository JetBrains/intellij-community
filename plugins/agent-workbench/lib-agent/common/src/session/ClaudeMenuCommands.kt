// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.common.session

private val CLAUDE_MENU_COMMANDS: List<ClaudeMenuCommand> = listOf(
  ClaudeMenuCommand("/agents"),
  ClaudeMenuCommand("/clear"),
  ClaudeMenuCommand("/compact", "[instructions]"),
  ClaudeMenuCommand("/config"),
  ClaudeMenuCommand("/doctor"),
  ClaudeMenuCommand("/init"),
  ClaudeMenuCommand("/login"),
  ClaudeMenuCommand("/logout"),
  ClaudeMenuCommand("/mcp"),
  ClaudeMenuCommand("/memory"),
  ClaudeMenuCommand("/model", "[model]"),
  ClaudeMenuCommand("/rename", "[title]"),
  ClaudeMenuCommand("/resume", "[session]"),
  ClaudeMenuCommand("/sandbox"),
  ClaudeMenuCommand("/status"),
)

fun claudeMenuCommands(): List<String> = CLAUDE_MENU_COMMANDS.map(ClaudeMenuCommand::command)

fun claudeMenuCommandEntries(): List<ClaudeMenuCommand> = CLAUDE_MENU_COMMANDS

data class ClaudeMenuCommand(
  val command: String,
  val argumentHint: String = "",
)

fun String.leadingSlashCommandToken(): String? {
  val normalized = trimStart()
  if (!normalized.startsWith('/')) {
    return null
  }
  return normalized.takeWhile { char -> !char.isWhitespace() }
}

fun String.isClaudeMenuCommandPrompt(): Boolean {
  val token = leadingSlashCommandToken() ?: return false
  return token in claudeMenuCommands()
}
