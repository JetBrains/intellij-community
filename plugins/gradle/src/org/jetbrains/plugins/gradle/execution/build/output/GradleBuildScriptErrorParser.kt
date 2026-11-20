// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.FilePosition
import com.intellij.build.eventBuilders.MessageEventBuilder
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import java.nio.file.Path

private const val SINGLE_FAILURE_HEADER = "FAILURE: Build failed with an exception."
private const val WHERE = "* Where:"
private const val WHAT_WENT_WRONG = "* What went wrong:"
private const val BUILD_FAILED = "BUILD FAILED"
private const val CONFIGURE_FAILED = "CONFIGURE FAILED"
private const val MULTIPLE_FAILURES_SEPARATOR = "====="
private const val GET_MORE_HELP = "> Get more help at https://help.gradle.org"
private const val LEGACY_GET_MORE_HELP = "* Get more help at https://help.gradle.org"
private const val EXCEPTION_IS = "* Exception is:"

private val MULTIPLE_FAILURES_HEADER_REGEX = Regex("^FAILURE: Build completed with (\\d+) failures")
private val SCRIPT_LOCATION_REGEX = Regex(
  "(?:[Bb]uild file|[Ss]ettings file|Initialization script|Script) " +
  "(^|[\\W])(?<file>(?:\\p{Alpha}:|/)[0-9 a-z_A-Z\\-\\\\./]+)'(?: line)?: (?<line>[0-9]+)"
)

/**
 * @author Vladislav.Soroka
 */
class GradleBuildScriptErrorParser : BuildOutputLineParserBridge() {

  private class Failure(val lines: List<BuildOutputText>, val filePosition: FilePosition?) {
    fun getTitle() = lines.first().text
  }

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    if (!isReportStart(line.text)) {
      return false
    }
    val reportLines = readEntireReport(line, reader)
    val failures = parseFailures(reportLines)
    report(reportLines, failures, taskId, multicaster, isSingleFlow)
    return true
  }

  private fun isReportStart(text: String): Boolean =
    text.startsWith(SINGLE_FAILURE_HEADER) || MULTIPLE_FAILURES_HEADER_REGEX.containsMatchIn(text)

  private suspend fun readEntireReport(header: BuildOutputText, reader: BuildOutputReplayableLineReader): List<BuildOutputText> {
    val multipleFailuresCountValue = MULTIPLE_FAILURES_HEADER_REGEX.find(header.text)?.groupValues?.get(1)?.toInt()
    val multipleFailures = multipleFailuresCountValue != null
    val failureCount = multipleFailuresCountValue ?: 1

    val lines = mutableListOf(header)
    repeat(failureCount) {
      while (true) {
        val line = reader.readLine() ?: return lines
        if (line.text.startsWith(BUILD_FAILED) || line.text.startsWith(CONFIGURE_FAILED)) {
          return lines
        }
        lines.add(line)
        if (isFailureEnd(line.text, multipleFailures)) {
          break
        }
      }
    }
    if (!multipleFailures) {
      lines.addAll(readExceptionIfPresent(reader))
    }
    return lines
  }

  private suspend fun readExceptionIfPresent(reader: BuildOutputReplayableLineReader): List<BuildOutputText> {
    val blankLine = reader.readLine() ?: return emptyList()
    val exceptionStart = reader.readLine()
    if (blankLine.text.isNotBlank() || exceptionStart?.text != EXCEPTION_IS) {
      val pushBack = if (exceptionStart == null) 1 else 2
      reader.pushBack(pushBack)
      return emptyList()
    }
    val lines = mutableListOf(blankLine, exceptionStart)
    while (true) {
      val line = reader.readLine() ?: break
      if (line.text.isBlank()) {
        reader.pushBack(1)
        break
      }
      lines.add(line)
    }
    return lines
  }

  private fun isFailureEnd(text: String, multipleFailures: Boolean): Boolean = when (multipleFailures) {
    true -> text.startsWith(MULTIPLE_FAILURES_SEPARATOR)
    else -> text.startsWith(GET_MORE_HELP) || text.startsWith(LEGACY_GET_MORE_HELP)
  }

  private fun parseFailures(lines: List<BuildOutputText>): List<Failure> {
    val failures = mutableListOf<Failure>()
    var filePosition: FilePosition? = null
    for ((index, line) in lines.withIndex()) {
      if (line.text == WHERE) {
        filePosition = lines.getOrNull(index + 1)?.let { parseScriptLocation(it.text) }
      }
      if (line.text == WHAT_WENT_WRONG) {
        val failureLines = lines.drop(index + 1).takeWhile { it.text.isNotBlank() }
        if (failureLines.isNotEmpty()) {
          failures.add(Failure(failureLines, filePosition))
        }
        filePosition = null
      }
    }
    return failures
  }

  private fun parseScriptLocation(location: String): FilePosition? {
    val match = SCRIPT_LOCATION_REGEX.find(location) ?: return null
    val path = match.groups["file"]?.value ?: return null
    val line = match.groups["line"]?.value?.toIntOrNull() ?: return null
    return FilePosition(Path.of(path), line - 1, 0)
  }

  private fun report(
    reportLines: List<BuildOutputText>,
    failures: List<Failure>,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ) {
    val reportId = Any()
    multicaster.notifyBuildEvent(
      MessageEvent.builder(reportLines.first().text, MessageEvent.Kind.ERROR)
        .withId(reportId)
        .withParentId(taskId)
        .withDescription(reportLines, isSingleFlow)
        .build()
    )
    for (failure in failures) {
      multicaster.notifyBuildEvent(
        MessageEvent.builder(failure.getTitle(), MessageEvent.Kind.ERROR)
          .withParentId(reportId)
          .withFilePosition(failure.filePosition)
          .withDescription(failure.lines, isSingleFlow)
          .build()
      )
    }
  }

  private fun MessageEventBuilder.withDescription(lines: List<BuildOutputText>, isSingleFlow: Boolean): MessageEventBuilder =
    when (isSingleFlow) {
      true -> withOutputIds(lines.flatMap { it.ids })
      else -> withDescription(lines.joinToString("\n") { it.text }) //NON-NLS
    }
}