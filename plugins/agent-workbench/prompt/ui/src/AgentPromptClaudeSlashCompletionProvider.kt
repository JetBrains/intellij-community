// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.claudeMenuCommandEntries
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.lookup.CharFilter
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.TextFieldCompletionProviderDumbAware
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val CLAUDE_DIRECTORY = ".claude"
private const val COMMANDS_DIRECTORY = "commands"
private const val SKILLS_DIRECTORY = "skills"
private const val SKILL_FILE_NAME = "SKILL.md"

internal class AgentPromptClaudeSlashCompletionProvider(
  private val selectedProvider: () -> AgentSessionProvider?,
  private val resolveWorkingProjectPaths: () -> List<String>,
) : TextFieldCompletionProviderDumbAware() {
  override fun getPrefix(text: String, offset: Int): String? {
    if (selectedProvider() != AgentSessionProvider.CLAUDE) {
      return null
    }
    return findClaudeSlashCompletionPrefix(text, offset)
  }

  override fun acceptChar(c: Char): CharFilter.Result? {
    return if (c == '/' || c == '-' || c == '_' || c.isLetterOrDigit()) CharFilter.Result.ADD_TO_PREFIX else null
  }

  override fun applyPrefixMatcher(result: CompletionResultSet, prefix: String): CompletionResultSet {
    return result.withPrefixMatcher(PlainPrefixMatcher(prefix, true)).caseInsensitive()
  }

  override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
    collectClaudeSlashCompletionEntries(resolveWorkingProjectPaths()).forEach { entry ->
      result.addElement(entry.toLookupElement())
    }
  }
}

internal fun findClaudeSlashCompletionPrefix(text: String, offset: Int): String? {
  val safeOffset = offset.coerceIn(0, text.length)
  val tokenStart = text.lastIndexOfAny(charArrayOf(' ', '\n', '\t', '\r'), startIndex = safeOffset - 1) + 1
  if (tokenStart >= safeOffset) {
    return null
  }

  val prefix = text.substring(tokenStart, safeOffset)
  return prefix.takeIf { candidate -> candidate.startsWith('/') }
}

internal fun shouldAutoPopupClaudeSlashCompletion(
  selectedProvider: AgentSessionProvider?,
  workingProjectPaths: Iterable<String?>,
  text: String,
  offsetAfterChange: Int,
  insertedFragment: CharSequence,
): Boolean {
  if (selectedProvider != AgentSessionProvider.CLAUDE) {
    return false
  }
  if (insertedFragment.length != 1 || insertedFragment[0] != '/') {
    return false
  }
  if (offsetAfterChange != 1 || !text.startsWith('/')) {
    return false
  }
  if (findClaudeSlashCompletionPrefix(text, offsetAfterChange) != "/") {
    return false
  }
  return collectClaudeSlashCompletionEntries(workingProjectPaths).isNotEmpty()
}

internal fun collectClaudeSlashCompletionEntries(workingProjectPath: String?): List<AgentPromptClaudeSlashCompletionEntry> {
  return collectClaudeSlashCompletionEntries(listOfNotNull(workingProjectPath))
}

internal fun collectClaudeSlashCompletionEntries(workingProjectPaths: Iterable<String?>): List<AgentPromptClaudeSlashCompletionEntry> {
  val entriesByKey = LinkedHashMap<AgentPromptClaudeSlashCompletionKey, AgentPromptClaudeSlashCompletionEntry>()

  collectClaudeMenuEntries().forEach { entry ->
    entriesByKey.putIfAbsent(AgentPromptClaudeSlashCompletionKey(entry.kind, entry.name), entry)
  }

  workingProjectPaths
    .asSequence()
    .mapNotNull(::parseSlashCompletionRoot)
    .distinct()
    .forEach { rootPath ->
      collectClaudeSlashCompletionEntriesForRoot(rootPath).forEach { entry ->
        entriesByKey.putIfAbsent(AgentPromptClaudeSlashCompletionKey(entry.kind, entry.name), entry)
      }
    }

  return entriesByKey.values.sortedWith(
    compareBy({ it.name }, { it.kind.sortOrder }, { it.sourceKey }),
  )
}

private fun collectClaudeSlashCompletionEntriesForRoot(rootPath: Path): List<AgentPromptClaudeSlashCompletionEntry> {
  val entriesByKey = LinkedHashMap<AgentPromptClaudeSlashCompletionKey, AgentPromptClaudeSlashCompletionEntry>()

  collectSlashCompletionAncestors(rootPath).forEach { ancestor ->
    collectClaudeCommandEntries(ancestor).forEach { entry ->
      entriesByKey.putIfAbsent(AgentPromptClaudeSlashCompletionKey(entry.kind, entry.name), entry)
    }
    collectClaudeSkillEntries(ancestor).forEach { entry ->
      entriesByKey.putIfAbsent(AgentPromptClaudeSlashCompletionKey(entry.kind, entry.name), entry)
    }
  }

  return entriesByKey.values.sortedWith(
    compareBy({ it.name }, { it.kind.sortOrder }, { it.sourceKey }),
  )
}

private fun collectClaudeMenuEntries(): List<AgentPromptClaudeSlashCompletionEntry> {
  return claudeMenuCommandEntries().map { command ->
    AgentPromptClaudeSlashCompletionEntry(
      name = command.command.removePrefix("/"),
      kind = AgentPromptClaudeSlashCompletionKind.MENU,
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

private fun collectClaudeCommandEntries(ancestor: Path): List<AgentPromptClaudeSlashCompletionEntry> {
  val commandsDirectory = ancestor.resolve(CLAUDE_DIRECTORY).resolve(COMMANDS_DIRECTORY)
  if (!Files.isDirectory(commandsDirectory)) {
    return emptyList()
  }

  Files.newDirectoryStream(commandsDirectory, "*.md").use { stream ->
    return stream.asSequence()
      .filter { path -> Files.isRegularFile(path) }
      .sortedBy { path -> path.fileName.toString() }
      .map { path ->
        AgentPromptClaudeSlashCompletionEntry(
          name = path.fileName.toString().removeSuffix(".md"),
          kind = AgentPromptClaudeSlashCompletionKind.COMMAND,
          sourceKey = path.toString(),
          sourcePath = path,
          argumentHint = readSlashCompletionArgumentHint(path),
        )
      }
      .toList()
  }
}

private fun collectClaudeSkillEntries(ancestor: Path): List<AgentPromptClaudeSlashCompletionEntry> {
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
        AgentPromptClaudeSlashCompletionEntry(
          name = path.fileName.toString(),
          kind = AgentPromptClaudeSlashCompletionKind.SKILL,
          sourceKey = path.resolve(SKILL_FILE_NAME).toString(),
          sourcePath = path.resolve(SKILL_FILE_NAME),
          argumentHint = readSlashCompletionArgumentHint(path.resolve(SKILL_FILE_NAME)),
        )
      }
      .toList()
  }
}

private fun AgentPromptClaudeSlashCompletionEntry.toLookupElement(): LookupElement {
  val builder = LookupElementBuilder.create(this, lookupString)
    .withPresentableText(lookupString)
    .withTypeText(kind.label, true)

  return (argumentHint.takeIf(String::isNotBlank)?.let { hint ->
    builder.withTailText(" $hint", true)
  } ?: builder).withInsertHandler { context, _ ->
    val tailOffset = context.tailOffset
    val chars = context.document.charsSequence
    if (tailOffset < chars.length && chars[tailOffset].isWhitespace()) {
      return@withInsertHandler
    }
    if (tailOffset == chars.length) {
      context.document.insertString(tailOffset, " ")
      context.tailOffset = tailOffset + 1
    }
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

internal data class AgentPromptClaudeSlashCompletionEntry(
  val name: String,
  val kind: AgentPromptClaudeSlashCompletionKind,
  val sourceKey: String,
  val sourcePath: Path? = null,
  val argumentHint: String = "",
) {
  val lookupString: String
    get() = "/$name"
}

internal enum class AgentPromptClaudeSlashCompletionKind(
  val sortOrder: Int,
  private val labelKey: String,
) {
  MENU(0, "popup.completion.type.menu"),
  COMMAND(1, "popup.completion.type.command"),
  SKILL(2, "popup.completion.type.skill"),
  ;

  val label: String
    get() = AgentPromptBundle.message(labelKey)
}

private data class AgentPromptClaudeSlashCompletionKey(
  val kind: AgentPromptClaudeSlashCompletionKind,
  val name: String,
)
