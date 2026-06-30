// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.claude.sessions

import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionMenuCommand
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionEntry
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionPromptCommandCompletionKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val CLAUDE_DIRECTORY = ".claude"
private const val COMMANDS_DIRECTORY = "commands"
private const val SKILLS_DIRECTORY = "skills"
private const val SKILL_FILE_NAME = "SKILL.md"

internal val CLAUDE_MENU_COMMANDS: List<AgentSessionMenuCommand> = listOf(
  AgentSessionMenuCommand("/agents"),
  AgentSessionMenuCommand("/clear"),
  AgentSessionMenuCommand("/compact", "[instructions]"),
  AgentSessionMenuCommand("/config"),
  AgentSessionMenuCommand("/doctor"),
  AgentSessionMenuCommand("/init"),
  AgentSessionMenuCommand("/login"),
  AgentSessionMenuCommand("/logout"),
  AgentSessionMenuCommand("/mcp"),
  AgentSessionMenuCommand("/memory"),
  AgentSessionMenuCommand("/model", "[model]"),
  AgentSessionMenuCommand("/rename", "[title]"),
  AgentSessionMenuCommand("/resume", "[session]"),
  AgentSessionMenuCommand("/sandbox"),
  AgentSessionMenuCommand("/status"),
)

internal fun collectClaudePromptCommandCompletionEntries(workingProjectPaths: Iterable<String?>): List<AgentSessionPromptCommandCompletionEntry> {
  val entriesByKey = LinkedHashMap<ClaudePromptCommandCompletionKey, AgentSessionPromptCommandCompletionEntry>()

  collectClaudeMenuEntries().forEach { entry ->
    entriesByKey.putIfAbsent(ClaudePromptCommandCompletionKey(entry.kind, entry.command), entry)
  }

  workingProjectPaths
    .asSequence()
    .mapNotNull(::parseSlashCompletionRoot)
    .distinct()
    .forEach { rootPath ->
      collectClaudePromptCommandCompletionEntriesForRoot(rootPath).forEach { entry ->
        entriesByKey.putIfAbsent(ClaudePromptCommandCompletionKey(entry.kind, entry.command), entry)
      }
    }

  return entriesByKey.values.sortedWith(
    compareBy({ it.command }, { it.kind.ordinal }, { it.sourceKey }),
  )
}

private fun collectClaudePromptCommandCompletionEntriesForRoot(rootPath: Path): List<AgentSessionPromptCommandCompletionEntry> {
  val entriesByKey = LinkedHashMap<ClaudePromptCommandCompletionKey, AgentSessionPromptCommandCompletionEntry>()

  collectSlashCompletionAncestors(rootPath).forEach { ancestor ->
    collectClaudeCommandEntries(ancestor).forEach { entry ->
      entriesByKey.putIfAbsent(ClaudePromptCommandCompletionKey(entry.kind, entry.command), entry)
    }
    collectClaudeSkillEntries(ancestor).forEach { entry ->
      entriesByKey.putIfAbsent(ClaudePromptCommandCompletionKey(entry.kind, entry.command), entry)
    }
  }

  return entriesByKey.values.sortedWith(
    compareBy({ it.command }, { it.kind.ordinal }, { it.sourceKey }),
  )
}

private fun collectClaudeMenuEntries(): List<AgentSessionPromptCommandCompletionEntry> {
  return CLAUDE_MENU_COMMANDS.map { command ->
    AgentSessionPromptCommandCompletionEntry(
      command = command.command,
      kind = AgentSessionPromptCommandCompletionKind.MENU,
      sourceKey = "builtin:menu:${command.command}",
      argumentHint = command.argumentHint,
    )
  }
}

private fun parseSlashCompletionRoot(workingProjectPath: String?): Path? {
  val pathString = workingProjectPath?.takeIf { path -> path.isNotBlank() } ?: return null
  return try {
    val parsedPath = Path.of(pathString).toAbsolutePath().normalize()
    if (Files.exists(parsedPath) && !Files.isDirectory(parsedPath)) parsedPath.parent else parsedPath
  }
  catch (_: InvalidPathException) {
    null
  }
}

private fun collectSlashCompletionAncestors(rootPath: Path): Sequence<Path> {
  return generateSequence(rootPath) { path -> path.parent }
}

private fun collectClaudeCommandEntries(ancestor: Path): List<AgentSessionPromptCommandCompletionEntry> {
  val commandsDirectory = ancestor.resolve(CLAUDE_DIRECTORY).resolve(COMMANDS_DIRECTORY)
  if (!Files.isDirectory(commandsDirectory)) {
    return emptyList()
  }

  Files.newDirectoryStream(commandsDirectory, "*.md").use { stream ->
    return stream.asSequence()
      .filter { path -> Files.isRegularFile(path) }
      .sortedBy { path -> path.fileName.toString() }
      .map { path ->
        AgentSessionPromptCommandCompletionEntry(
          command = "/" + path.fileName.toString().removeSuffix(".md"),
          kind = AgentSessionPromptCommandCompletionKind.COMMAND,
          sourceKey = path.toString(),
          sourcePath = path,
          argumentHint = readSlashCompletionArgumentHint(path),
        )
      }
      .toList()
  }
}

private fun collectClaudeSkillEntries(ancestor: Path): List<AgentSessionPromptCommandCompletionEntry> {
  val skillsDirectory = ancestor.resolve(CLAUDE_DIRECTORY).resolve(SKILLS_DIRECTORY)
  if (!Files.isDirectory(skillsDirectory)) {
    return emptyList()
  }

  Files.newDirectoryStream(skillsDirectory).use { stream ->
    return stream.asSequence()
      .filter { path -> Files.isDirectory(path) }
      .filter { path -> Files.isRegularFile(path.resolve(SKILL_FILE_NAME)) }
      .sortedBy { path -> path.fileName.toString() }
      .map { path ->
        val skillFile = path.resolve(SKILL_FILE_NAME)
        AgentSessionPromptCommandCompletionEntry(
          command = "/" + path.fileName.toString(),
          kind = AgentSessionPromptCommandCompletionKind.SKILL,
          sourceKey = skillFile.toString(),
          sourcePath = skillFile,
          argumentHint = readSlashCompletionArgumentHint(skillFile),
        )
      }
      .toList()
  }
}

private fun readSlashCompletionArgumentHint(path: Path): String {
  return readArgumentHintFrontmatterValue(path) ?: ""
}

private fun readArgumentHintFrontmatterValue(path: Path): String? {
  return try {
    Files.newBufferedReader(path).use { reader ->
      val lines = reader.lineSequence().iterator()
      if (!lines.hasNext() || lines.next().trim() != "---") {
        return null
      }

      while (lines.hasNext()) {
        val line = lines.next().trim()
        if (line == "---") {
          return null
        }

        val argumentHint = parseArgumentHintLine(line)
        if (argumentHint != null) {
          return argumentHint
        }
      }

      null
    }
  }
  catch (_: IOException) {
    null
  }
}

private fun parseArgumentHintLine(line: String): String? {
  for (prefix in listOf("argument-hint:", "argument_hint:")) {
    if (line.startsWith(prefix)) {
      return line.substring(prefix.length)
        .trim()
        .trimMatchingQuotes()
        .takeIf(String::isNotBlank)
    }
  }
  return null
}

private fun String.trimMatchingQuotes(): String {
  if (length >= 2 && first() == last() && (first() == '"' || first() == '\'')) {
    return substring(1, lastIndex)
  }
  return this
}

private data class ClaudePromptCommandCompletionKey(
  val kind: AgentSessionPromptCommandCompletionKind,
  val command: String,
)
