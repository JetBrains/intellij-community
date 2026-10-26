// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import com.intellij.build.output.KotlincOutputParser
import org.jetbrains.annotations.ApiStatus

/**
 * Parses the multi-line report from Kotlin Gradle plugin and Gradle Kotlin DSL plugin.
 * A message starts with a severity prefix (`w:`, `e:`, `WARNING:` or `ERROR:`) and a title without a file location.
 * A report with a file location processed by [KotlincOutputParser].
 */
@ApiStatus.Internal
class GradleKotlinErrorReporterOutputParser : BuildOutputLineParserBridge() {

  private companion object {
    private val SOURCE_FILE_EXTENSIONS = listOf(".kt", ".kts", ".java")
    private val ABSOLUTE_PATH_REGEX = "^(file:|/|\\\\|[A-Za-z]:[/\\\\])".toRegex()

    private val MESSAGE_END_PREFIXES = listOf(
      "> ",
      "FAILURE:",
      "BUILD SUCCESSFUL",
      "BUILD FAILED",
      "CONFIGURE SUCCESSFUL",
      "CONFIGURE FAILED",
      "[Incubating] Problems report",
    )
  }

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    val kind = getMessageKind(line.text) ?: return false
    val title = line.text.substringAfter(":").trim()
    if (title.isEmpty() || title.startsWithFileLocation()) {
      return false
    }

    val lines = mutableListOf(line)
    var prevLineBlank = false
    while (true) {
      val nextLine = reader.readLine() ?: break
      if (nextLine.text.isMessageEnd()) {
        reader.pushBack(1)
        break
      }
      val isBlank = nextLine.text.isBlank()
      if (isBlank && prevLineBlank) {
        break
      }
      prevLineBlank = isBlank
      lines.add(nextLine)
    }

    val description = (listOf(title) + lines.drop(1).map { it.text })
      .joinToString("\n")
      .trimEnd()
    multicaster.notifyBuildEvent(
      MessageEvent.builder(title, kind)
        .withParentId(taskId)
        .withDescription(lines, description, isSingleFlow)
        .build()
    )
    return true
  }

  private fun getMessageKind(text: String): MessageEvent.Kind? {
    val colonIndex = text.indexOf(":")
    if (colonIndex <= 0) {
      return null
    }
    val kind = text.substring(0, colonIndex)
    return when (kind) {
      "w", "WARNING" -> MessageEvent.Kind.WARNING
      "e", "ERROR" -> MessageEvent.Kind.ERROR
      else -> null
    }
  }

  private fun String.startsWithFileLocation(): Boolean {
    val path = KotlincOutputParser.extractPath(this)?.trim() ?: return false
    return SOURCE_FILE_EXTENSIONS.any { path.endsWith(it, ignoreCase = true) } || ABSOLUTE_PATH_REGEX.containsMatchIn(path)
  }

  private fun String.isMessageEnd(): Boolean = getMessageKind(this) != null || MESSAGE_END_PREFIXES.any { startsWith(it) }

  private fun MessageEventBuilder.withDescription(
    lines: List<BuildOutputText>,
    description: String,
    isSingleFlow: Boolean,
  ): MessageEventBuilder =
    when (isSingleFlow) {
      true -> withOutputIds(lines.flatMap { it.ids })
      else -> withDescription(description)
    }
}
