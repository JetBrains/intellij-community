// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.build.output

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.DuplicateMessageAware
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.StartId
import com.intellij.build.events.impl.BuildIssueEventImpl
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReaderImpl.BuildOutputLineParserBridge
import com.intellij.build.output.BuildOutputMulticaster
import com.intellij.build.output.BuildOutputReplayableLineReader
import com.intellij.build.output.BuildOutputText
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.plugins.gradle.execution.GradleConsoleFilter
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.UnresolvedDependencyBuildIssue
import java.nio.file.Path
import java.util.function.Consumer

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
    val filter: GradleConsoleFilter?
    var whereOrWhatLine = reader.readLine()?.text
    if (whereOrWhatLine == "* Where:") {
      location = reader.readLine()?.text ?: return false
      filter = GradleConsoleFilter(null)
      filter.applyFilter(location, location.length) ?: return false
      if (!reader.readLine()?.text.isNullOrBlank()) return false
      whereOrWhatLine = reader.readLine()?.text
    }
    else {
      location = null
      filter = null
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

    // compilation errors should be added by the respective compiler output parser
    if (reason.startsWith("Compilation failed") ||
        reason == "Compilation error. See log for more details" ||
        reason == "Script compilation error:" ||
        reason.contains("compiler failed")) return false

    val filePosition: FilePosition?
    if (filter != null) {
      filePosition = FilePosition(Path.of(filter.filteredFileName), filter.filteredLineNumber - 1, 0)
    }
    else {
      filePosition = null
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

    val detailedMessage = StringBuilder(errorText)

    if (!trySuggestions.isNullOrBlank()) {
      detailedMessage.append("\n* Try:\n$trySuggestions")
    }
    if (!exception.isNullOrBlank()) {
      detailedMessage.append("\n* Exception is:\n$exception")
    }
    if (filePosition != null) {
      //TODO: Used english suggestions and exceptions:
      multicaster.notifyBuildEvent(object : FileMessageEventImpl(
        parentId, MessageEvent.Kind.ERROR, null, reason, detailedMessage.toString(), filePosition), DuplicateMessageAware {} //NON-NLS
      )
    }
    else {
      val unresolvedMessageEvent = checkUnresolvedDependencyError(reason, errorText, parentId)
      if (unresolvedMessageEvent != null) {
        multicaster.notifyBuildEvent(unresolvedMessageEvent)
      }
      else {
        //TODO: Used english suggestions and exceptions:
        multicaster.notifyBuildEvent(object : MessageEventImpl(parentId, MessageEvent.Kind.ERROR, null, reason,
                                                         detailedMessage.toString()), DuplicateMessageAware {}) //NON-NLS
      }
    }
    return true
  }

  private fun checkUnresolvedDependencyError(reason: String, description: String, parentId: Any): BuildEvent? {
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
    return BuildIssueEventImpl(parentId, unresolvedDependencyIssue, MessageEvent.Kind.ERROR)
  }
}

