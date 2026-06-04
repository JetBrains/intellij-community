// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.issue

import com.intellij.build.FilePosition
import com.intellij.openapi.util.text.StringUtil
import java.nio.file.Path
import java.util.regex.Pattern

internal class GradleIssueFailureImpl(
  override val message: String?,
  override val description: String?,
  override val causes: List<GradleIssueFailure>,
) : GradleIssueFailure {

  override val rootCause: GradleIssueFailure = causes.resolveForNonEmptyCauses { it.rootCause } ?: this

  override val filePosition: FilePosition? = resolveFilePosition(message, description, causes)

  override val className: String? = resolveClassName(description)

  override val text: String = resolveFailureText(message, description, className) ?: ""
}

internal class GradleThrowableIssueFailure(
  val throwable: Throwable,
) : GradleIssueFailure {

  override val message: String? = throwable.message

  override val description: String = throwable.stackTraceToString()

  override val causes: List<GradleThrowableIssueFailure> = resolveCauses(throwable)

  override val rootCause: GradleThrowableIssueFailure = causes.resolveForNonEmptyCauses { it.rootCause } ?: this

  override val filePosition: FilePosition? = resolveFilePosition(message, description, causes)

  override val className: String = throwable.javaClass.name

  override val text: String = resolveFailureText(message, description, className) ?: ""
}

private fun resolveCauses(throwable: Throwable): List<GradleThrowableIssueFailure> {
  val cause = throwable.cause ?: return emptyList()
  if (cause === throwable) return emptyList()
  return listOf(GradleThrowableIssueFailure(cause))
}

private fun <T : GradleIssueFailure, R> List<T>.resolveForNonEmptyCauses(resolve: (T) -> R?): R? =
  asSequence()
    .filterNot { it.message == null && it.description == null && it.className != StackOverflowError::class.java.name }
    .firstNotNullOfOrNull { resolve(it) }

private fun resolveFilePosition(message: String?, description: String?, causes: List<GradleIssueFailure>): FilePosition? =
  GradleIssueFailureLocationResolver.getFilePositionFromText(message)
  ?: GradleIssueFailureLocationResolver.getFilePositionFromText(description)
  ?: causes.resolveForNonEmptyCauses { it.filePosition }

private fun resolveClassName(description: String?): String? {
  val description = description ?: return null
  val firstLine = description.lineSequence().firstOrNull() ?: return null
  val candidate = firstLine.substringBefore(':').trim()
  if (candidate.indexOf('.') == -1 || candidate.indexOf(' ') != -1) return null
  return candidate
}

private fun resolveFailureText(message: String?, description: String?, className: String?): String? {
  return when {
    className != null && message != null -> "$className: $message"
    className != null && description != null -> "$className: $description"
    message != null -> message
    description != null -> description
    className != null -> className
    else -> null
  }
}

private object GradleIssueFailureLocationResolver {

  private val ERROR_LOCATION_IN_FILE_PATTERN = Pattern.compile("(?:Build|Settings) file '(.*)' line: (\\d+)")
  private val ERROR_IN_FILE_PATTERN = Pattern.compile("(?:Build|Settings) file '(.*)'")
  private val STACK_TRACE_SCRIPT_LOCATION_PATTERN = Pattern.compile("\\((.*\\.(?:gradle\\.kts|gradle)):(\\d+)\\)")

  fun getFilePositionFromText(text: String?): FilePosition? {
    if (text.isNullOrEmpty()) return null
    for (line in StringUtil.splitByLines(text)) {
      val filePosition = getFilePositionFromLocation(line)
      if (filePosition != null) return filePosition
    }
    return null
  }

  private fun getFilePositionFromLocation(location: String?): FilePosition? {
    if (location == null) return null

    val locationMatcher = ERROR_LOCATION_IN_FILE_PATTERN.matcher(location)
    if (locationMatcher.find()) {
      val line = locationMatcher.group(2).toIntOrNull() ?: -1
      return FilePosition(Path.of(locationMatcher.group(1)), line, 0)
    }

    val fileMatcher = ERROR_IN_FILE_PATTERN.matcher(location)
    if (fileMatcher.find()) {
      return FilePosition(Path.of(fileMatcher.group(1)), -1, 0)
    }

    val stackTraceMatcher = STACK_TRACE_SCRIPT_LOCATION_PATTERN.matcher(location)
    if (stackTraceMatcher.find()) {
      val line = stackTraceMatcher.group(2).toIntOrNull() ?: -1
      return FilePosition(Path.of(stackTraceMatcher.group(1)), line, 0)
    }

    return null
  }
}
