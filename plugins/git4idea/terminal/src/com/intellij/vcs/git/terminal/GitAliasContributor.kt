// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.terminal

import com.intellij.terminal.completion.spec.ShellAliasSuggestion
import com.intellij.terminal.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.ShellAliasSuggestion
import org.jetbrains.plugins.terminal.block.completion.spec.ShellRuntimeDataGenerator
import org.jetbrains.plugins.terminal.block.completion.spec.dsl.ShellCommandContext

internal const val GET_ALIASES_COMMAND = "git config --get-regexp \"^alias\""

/**
 * Parses a Git alias string of the general format 'alias.{alias name} [!]{replacement}' into its parts.
 *
 * TODO: Implement special handling of commands starting with exclamation marks
 * Example: given `alias.ls !ll -la`, `git ls` should suggest arguments for `ls -la`, not `git ls -la`.
 */
internal fun parseGitAlias(line: String): Pair<String, String>? {
  if (!line.contains(' ')) return null

  val alias = line.removePrefix("alias.").substringBefore(' ').trim()
  val command = line.substringAfter(' ').trim()

  val prefixedCommand =
    if (command.startsWith('!')) command.substring(1)
    else command

  return alias to prefixedCommand
}

internal fun aliasGenerator(): ShellRuntimeDataGenerator<List<ShellAliasSuggestion>> =
  ShellRuntimeDataGenerator("git-aliases") { context ->
    val result = context.runShellCommand(GET_ALIASES_COMMAND)
    if (result.exitCode != 0) return@ShellRuntimeDataGenerator listOf()

    result.output.lines()
      .mapNotNull(::parseGitAlias)
      .map { (name, aliasValue) -> ShellAliasSuggestion(name, aliasValue) }
  }

internal fun ShellCommandContext.addGitAliases() {
  argument {
    optional()
    suggestions(aliasGenerator())
  }
}
