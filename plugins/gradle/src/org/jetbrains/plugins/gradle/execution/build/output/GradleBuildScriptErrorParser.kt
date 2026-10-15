// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.OutputId
import com.intellij.build.events.StartId
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import com.intellij.execution.filters.RegexpFilter
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue
import java.nio.file.Path
import java.util.function.Consumer
import java.util.regex.Pattern

/**
 * @author Vladislav.Soroka
 */
class GradleBuildScriptErrorParser : BuildOutputLineParserBridge() {

  override suspend fun parse(
    line: BuildOutputText,
    reader: BuildOutputReplayableLineReader,
    taskId: StartId,
    multicaster: BuildOutputMulticaster,
    isSingleFlow: Boolean,
  ): Boolean {
    if (!line.text.startsWith("FAILURE: Build failed with an exception.")) return false
    if (!reader.readLine()?.text.isNullOrBlank()) return false

    val location: String?
    val filePosition: FilePosition?
    var whereOrWhatLine = reader.readLine()?.text
    if (whereOrWhatLine == "* Where:") {
      location = reader.readLine()?.text ?: return false
      filePosition = parseScriptLocation(location) ?: return false
      if (!reader.readLine()?.text.isNullOrBlank()) return false
      whereOrWhatLine = reader.readLine()?.text
    }
    else {
      location = null
      filePosition = null
    }

    if (whereOrWhatLine != "* What went wrong:") return false

    val description = StringBuilder()
    if (location != null) {
      description.appendLine(location).appendLine()
    }
    @NlsSafe var reason = reader.readLine()?.text ?: return false
    val parentId: Any
    if (reason.startsWith("Execution failed for task '")) {
      val taskName = reason.substringAfter("Execution failed for task '").substringBefore("'.")
        .substringBefore("' (registered in build file")
        .substringBefore("' (registered by plugin class")
      parentId = GradleOutputDispatcherFactory.TaskNameId(taskName)
    }
    else {
      parentId = taskId
    }

    description.appendLine(reason)
    loop@ while (true) {
      val nextLine = reader.readLine()?.text ?: return false
      if (nextLine.isBlank()) break
      if (nextLine.isCompilationError()) {
        return false
      }
      description.appendLine(nextLine)
      val trimStart = nextLine.trimStart()
      if (trimStart.startsWith("> ")) {
        reason = trimStart.substringAfter("> ").trimEnd('.') //NON-NLS
      }
      when {
        nextLine.isEmpty() -> break@loop
        nextLine == "* Try:" -> {
          reader.pushBack(1)
          break@loop
        }
      }
    }

    var trySuggestions: StringBuilder? = null
    var exception: StringBuilder? = null
    while (true) {
      val nextLine = reader.readLine()?.text ?: break
      if (nextLine == "BUILD FAILED" || nextLine == "* Get more help at https://help.gradle.org" || nextLine.startsWith(
          "CONFIGURE FAILED")) break
      if (nextLine == "* Exception is:") {
        exception = StringBuilder()
      }
      else if (nextLine == "* Try:") {
        trySuggestions = StringBuilder()
      }
      else {
        if (exception != null) {
          exception.appendLine(nextLine)
        }
        else if (trySuggestions != null && nextLine.isNotBlank()) {
          trySuggestions.appendLine(nextLine)
        }
      }
    }

    if (reason.isCompilationError()) {
      return false
    }

    val errorText = description.toString()
    for (issueChecker in GradleIssueChecker.getKnownIssuesCheckList()) {
      if (issueChecker.consumeBuildOutputFailureMessage(errorText,
                                                        reason,
                                                        exception.toString(),
                                                        filePosition,
                                                        parentId,
                                                        Consumer<BuildEvent> { t -> multicaster.notifyBuildEvent(t) })
      ) {
        return true
      }
    }

    val buildEventDescription: String?
    val outputIds: List<OutputId>
    if (isSingleFlow) {
      outputIds = line.ids
      buildEventDescription = null
    }
    else {
      outputIds = emptyList()

      val detailedMessage = StringBuilder(errorText)
      if (!trySuggestions.isNullOrBlank()) {
        detailedMessage.append("\n* Try:\n$trySuggestions")
      }
      if (!exception.isNullOrBlank()) {
        detailedMessage.append("\n* Exception is:\n$exception")
      }
      buildEventDescription = detailedMessage.toString()
    }

    if (filePosition != null) {
      //TODO: Used english suggestions and exceptions:
      multicaster.notifyBuildEvent(
        object : FileMessageEventImpl(
          /* id = */ null,
          /* parentId = */ parentId,
          /* time = */ null,
          /* message = */ reason,
          /* hint = */ null,
          /* description = */ buildEventDescription,
          /* kind = */ MessageEvent.Kind.ERROR,
          /* group = */ null,
          /* navigatable = */ null,
          /* outputIds = */ outputIds,
          /* filePosition = */ filePosition
        ), DuplicateMessageAware {} //NON-NLS
      )
    }
    else {
      val unresolvedMessageEvent = checkUnresolvedDependencyError(reason, errorText, parentId, outputIds)
      if (unresolvedMessageEvent != null) {
        multicaster.notifyBuildEvent(unresolvedMessageEvent)
      }
      else {
        //TODO: Used english suggestions and exceptions:
        multicaster.notifyBuildEvent(
          object : MessageEventImpl(
            /* id = */ null,
            /* parentId = */ parentId,
            /* time = */ null,
            /* message = */ reason,
            /* hint = */ null,
            /* description = */ buildEventDescription,
            /* kind = */ MessageEvent.Kind.ERROR,
            /* group = */ null,
            /* navigatable = */ null,
            /* outputIds = */ outputIds
          ), DuplicateMessageAware {}
        ) //NON-NLS
      }
    }
    return true
  }

  private fun parseScriptLocation(location: String): FilePosition? {
    val matcher = SCRIPT_LOCATION_PATTERN.matcher(location)
    if (!matcher.find()) {
      return null
    }
    val path = matcher.group(FILE_GROUP) ?: return null
    val line = matcher.group(LINE_GROUP)?.toIntOrNull() ?: return null
    return FilePosition(Path.of(path), line - 1, 0)
  }

  private fun String.isCompilationError(): Boolean = contains("Compilation failed") ||
                                                     this == "Compilation error. See log for more details" ||
                                                     this == "Script compilation error:" ||
                                                     this.contains("compiler failed")

  private fun checkUnresolvedDependencyError(reason: String, description: String, parentId: Any, outputIds: List<OutputId>): BuildEvent? {
    val noCachedVersionPrefix = "No cached version of "
    val couldNotFindPrefix = "Could not find "
    val cannotResolvePrefix = "Cannot resolve external dependency "
    val cannotDownloadPrefix = "Could not download "
    val prefix = when {
                   reason.startsWith(noCachedVersionPrefix) -> noCachedVersionPrefix
                   reason.startsWith(couldNotFindPrefix) -> couldNotFindPrefix
                   reason.startsWith(cannotResolvePrefix) -> cannotResolvePrefix
                   reason.startsWith(cannotDownloadPrefix) -> cannotDownloadPrefix
                   else -> null
                 } ?: return null
    val indexOfSuffix = reason.indexOf(" available for offline mode")
    val dependencyName = if (indexOfSuffix > 0) reason.substring(prefix.length, indexOfSuffix) else reason.substring(prefix.length)
    val unresolvedDependencyIssue = UnresolvedDependencyBuildIssue(dependencyName, description, indexOfSuffix > 0)
    return BuildIssueEventImpl(
      /* id = */ null,
      /* parentId = */ parentId,
      /* time = */ null,
      /* hint = */ null,
      /* outputIds = */ outputIds,
      /* buildIssue = */ unresolvedDependencyIssue,
      /* kind = */ MessageEvent.Kind.ERROR
    )
  }

  private companion object {
    private const val SCRIPT_LOCATION_EXPRESSION = "(?:[Bb]uild file|[Ss]ettings file|Initialization script|Script) " +
                                                   "(^|[\\W])(?<file>(?:\\p{Alpha}\\:|/)[0-9 a-z_A-Z\\-\\\\./]+)'(?: line)?: (?<line>[0-9]+)"
    private const val FILE_GROUP = "file"
    private const val LINE_GROUP = "line"

    private val SCRIPT_LOCATION_PATTERN = Pattern.compile(SCRIPT_LOCATION_EXPRESSION, Pattern.MULTILINE)
  }
}

