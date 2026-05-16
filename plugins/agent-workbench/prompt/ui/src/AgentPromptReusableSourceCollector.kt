// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceEntry
import com.intellij.agent.workbench.prompt.core.AgentPromptReusableSourceKind
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private const val GITHUB_DIRECTORY = ".github"
private const val PROMPTS_DIRECTORY = "prompts"
private const val PROMPT_FILE_SUFFIX = ".prompt.md"

internal fun collectReusablePromptSourceEntries(
  workingProjectPaths: Iterable<String?>,
): List<AgentPromptReusableSourceEntry> {
  val entriesById = LinkedHashMap<String, AgentPromptReusableSourceEntry>()

  collectPromptFileEntries(workingProjectPaths).forEach { entry -> entriesById.putIfAbsent(entry.id, entry) }

  return entriesById.values.sortedWith(compareBy({ it.kind.sortOrder }, { it.label }))
}

internal fun collectPromptFileEntries(workingProjectPaths: Iterable<String?>): List<AgentPromptReusableSourceEntry> {
  val entriesById = LinkedHashMap<String, AgentPromptReusableSourceEntry>()
  workingProjectPaths
    .asSequence()
    .mapNotNull(::parseReusableSourceRoot)
    .distinct()
    .flatMap(::collectReusableSourceAncestors)
    .forEach { ancestor ->
      collectPromptFileEntriesForRoot(ancestor).forEach { entry -> entriesById.putIfAbsent(entry.id, entry) }
    }
  return entriesById.values.toList()
}

private fun collectPromptFileEntriesForRoot(rootPath: Path): List<AgentPromptReusableSourceEntry> {
  val promptsDirectory = rootPath.resolve(GITHUB_DIRECTORY).resolve(PROMPTS_DIRECTORY)
  if (!Files.isDirectory(promptsDirectory)) {
    return emptyList()
  }

  return try {
    Files.newDirectoryStream(promptsDirectory, "*$PROMPT_FILE_SUFFIX").use { stream ->
      stream.asSequence()
        .filter { path -> Files.isRegularFile(path) }
        .sortedBy { path -> path.fileName.toString() }
        .mapNotNull(::readPromptFileEntry)
        .toList()
    }
  }
  catch (_: IOException) {
    emptyList()
  }
}

private fun readPromptFileEntry(path: Path): AgentPromptReusableSourceEntry? {
  val rawText = try {
    Files.newBufferedReader(path).use { reader -> reader.readText() }
  }
  catch (_: IOException) {
    return null
  }

  val parsed = parsePromptFile(rawText)
  val insertText = parsed.body.trim().takeIf(String::isNotBlank) ?: return null
  val fileName = path.fileName.toString().removeSuffix(PROMPT_FILE_SUFFIX)
  return AgentPromptReusableSourceEntry(
    id = "prompt-file:${path.toAbsolutePath().normalize()}",
    label = parsed.name ?: parsed.title ?: fileName,
    insertText = insertText,
    kind = AgentPromptReusableSourceKind.PROMPT_FILE,
    description = parsed.description,
    sourcePath = path.toString(),
  )
}

private fun parseReusableSourceRoot(workingProjectPath: String?): Path? {
  val pathString = workingProjectPath?.takeIf { path -> path.isNotBlank() } ?: return null
  return try {
    val parsedPath = Path.of(pathString).toAbsolutePath().normalize()
    if (Files.exists(parsedPath) && !Files.isDirectory(parsedPath)) parsedPath.parent else parsedPath
  }
  catch (_: InvalidPathException) {
    null
  }
}

private fun collectReusableSourceAncestors(rootPath: Path): Sequence<Path> {
  return generateSequence(rootPath) { path -> path.parent }
}

private fun parsePromptFile(text: String): ParsedPromptFile {
  val normalizedText = text.replace("\r\n", "\n").replace('\r', '\n')
  if (!normalizedText.startsWith("---\n")) {
    return ParsedPromptFile(body = normalizedText)
  }

  val endMarker = normalizedText.indexOf("\n---", startIndex = 4)
  if (endMarker < 0) {
    return ParsedPromptFile(body = normalizedText)
  }

  val frontmatter = normalizedText.substring(4, endMarker)
  val bodyStart = (endMarker + "\n---".length).let { index ->
    if (index < normalizedText.length && normalizedText[index] == '\n') index + 1 else index
  }
  return ParsedPromptFile(
    name = readSimpleFrontmatterValue(frontmatter, "name"),
    title = readSimpleFrontmatterValue(frontmatter, "title"),
    description = readSimpleFrontmatterValue(frontmatter, "description"),
    body = normalizedText.substring(bodyStart),
  )
}

private fun readSimpleFrontmatterValue(frontmatter: String, key: String): String? {
  val prefix = "$key:"
  return frontmatter.lineSequence()
    .firstOrNull { line -> line.trimStart().startsWith(prefix) }
    ?.trim()
    ?.substring(prefix.length)
    ?.trim()
    ?.trimMatchingQuotes()
    ?.takeIf(String::isNotBlank)
}

private fun String.trimMatchingQuotes(): String {
  if (length >= 2 && first() == last() && (first() == '"' || first() == '\'')) {
    return substring(1, lastIndex)
  }
  return this
}

private val AgentPromptReusableSourceKind.sortOrder: Int
  get() = when (this) {
    AgentPromptReusableSourceKind.PROMPT_FILE -> 0
    AgentPromptReusableSourceKind.COMMAND -> 1
    AgentPromptReusableSourceKind.SKILL -> 2
  }

private data class ParsedPromptFile(
  @JvmField val name: String? = null,
  @JvmField val title: String? = null,
  @JvmField val description: String? = null,
  @JvmField val body: String,
)
