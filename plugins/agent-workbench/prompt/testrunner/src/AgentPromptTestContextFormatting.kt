// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner

private const val JAVA_TEST_URL_PREFIX = "java:test://"
private const val JAVA_SUITE_URL_PREFIX = "java:suite://"

private val PRIMARY_STATUS_ORDER = listOf("failed", "passed", "ignored", "inProgress", "unknown")

internal fun normalizeTestStatus(rawStatus: String?): String {
  val normalized = rawStatus
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?: return "unknown"
  return when (normalized) {
    "failed", "passed", "ignored", "inProgress", "unknown" -> normalized
    else -> normalized
  }
}

internal fun formatTestReference(name: String, locationUrl: String?): String {
  val normalizedName = name.trim()
  val normalizedLocation = locationUrl
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
  val locationReference = normalizedLocation?.let(::toDisplayReferenceFromLocationUrl)
  return when {
    !locationReference.isNullOrEmpty() -> locationReference
    normalizedName.isNotEmpty() -> normalizedName
    !normalizedLocation.isNullOrEmpty() -> normalizedLocation
    else -> "<unnamed test>"
  }
}

private fun toDisplayReferenceFromLocationUrl(locationUrl: String): String {
  if (locationUrl.startsWith(JAVA_TEST_URL_PREFIX)) {
    val path = locationUrl.removePrefix(JAVA_TEST_URL_PREFIX)
    return parseJavaLocationPath(path) ?: locationUrl
  }
  if (locationUrl.startsWith(JAVA_SUITE_URL_PREFIX)) {
    val path = locationUrl.removePrefix(JAVA_SUITE_URL_PREFIX)
    return path.trim().ifEmpty { locationUrl }
  }
  return locationUrl
}

private fun parseJavaLocationPath(path: String): String? {
  val normalizedPath = path.trim()
  if (normalizedPath.isEmpty()) {
    return null
  }

  val slashIndex = normalizedPath.indexOf('/')
  if (slashIndex > 0) {
    val className = normalizedPath.substring(0, slashIndex).trim()
    val methodName = normalizeMethodName(normalizedPath.substring(slashIndex + 1))
    return when {
      className.isNotEmpty() && methodName.isNotEmpty() -> "$className#$methodName"
      className.isNotEmpty() -> className
      else -> null
    }
  }

  val dotIndex = normalizedPath.lastIndexOf('.')
  if (dotIndex > 0 && dotIndex < normalizedPath.lastIndex) {
    val className = normalizedPath.substring(0, dotIndex).trim()
    val methodName = normalizeMethodName(normalizedPath.substring(dotIndex + 1))
    if (className.isNotEmpty() && methodName.isNotEmpty()) {
      return "$className#$methodName"
    }
  }

  return normalizedPath
}

private fun normalizeMethodName(rawMethodName: String): String {
  val trimmed = rawMethodName.trim()
  if (trimmed.isEmpty()) {
    return ""
  }
  return trimmed.removeSuffix("()")
}

internal fun computeTestStatusCounts(statuses: Iterable<String>): LinkedHashMap<String, Int> {
  val aggregated = LinkedHashMap<String, Int>()
  statuses.forEach { status ->
    val normalizedStatus = normalizeTestStatus(status)
    aggregated[normalizedStatus] = (aggregated[normalizedStatus] ?: 0) + 1
  }
  return orderStatusCounts(aggregated)
}

internal fun normalizeTestStatusCounts(rawCounts: Map<String, Int>): LinkedHashMap<String, Int> {
  val aggregated = LinkedHashMap<String, Int>()
  rawCounts.forEach { (status, count) ->
    if (count <= 0) {
      return@forEach
    }
    val normalizedStatus = normalizeTestStatus(status)
    aggregated[normalizedStatus] = (aggregated[normalizedStatus] ?: 0) + count
  }
  return orderStatusCounts(aggregated)
}

internal fun composeTestsGroupLabel(statusCounts: Map<String, Int>): String {
  val nonZeroCounts = orderStatusCounts(statusCounts)
    .filterValues { count -> count > 0 }
  if (nonZeroCounts.isEmpty()) {
    return AgentPromptTestRunnerBundle.message("context.tests.label.selected")
  }

  if (nonZeroCounts.size == 1) {
    return when (nonZeroCounts.keys.single()) {
      "failed" -> AgentPromptTestRunnerBundle.message("context.tests.label.failed")
      "passed" -> AgentPromptTestRunnerBundle.message("context.tests.label.passed")
      else -> AgentPromptTestRunnerBundle.message("context.tests.label.selected")
    }
  }

  val summary = nonZeroCounts.entries.joinToString(separator = " ") { (status, count) ->
    "${toStatusDisplayName(status)}:$count"
  }
  return AgentPromptTestRunnerBundle.message("context.tests.label.selected.with.counts", summary)
}

private fun orderStatusCounts(statusCounts: Map<String, Int>): LinkedHashMap<String, Int> {
  val normalized = LinkedHashMap<String, Int>()
  statusCounts.forEach { (status, count) ->
    val normalizedStatus = normalizeTestStatus(status)
    normalized[normalizedStatus] = (normalized[normalizedStatus] ?: 0) + count
  }

  val ordered = LinkedHashMap<String, Int>()
  PRIMARY_STATUS_ORDER.forEach { status ->
    val count = normalized[status]
    if (count != null) {
      ordered[status] = count
    }
  }
  normalized.keys
    .asSequence()
    .filterNot { status -> PRIMARY_STATUS_ORDER.contains(status) }
    .sorted()
    .forEach { status ->
      ordered[status] = normalized.getValue(status)
    }
  return ordered
}

private fun toStatusDisplayName(status: String): String {
  return when (status) {
    "failed" -> AgentPromptTestRunnerBundle.message("context.tests.status.failed")
    "passed" -> AgentPromptTestRunnerBundle.message("context.tests.status.passed")
    "ignored" -> AgentPromptTestRunnerBundle.message("context.tests.status.ignored")
    "inProgress" -> AgentPromptTestRunnerBundle.message("context.tests.status.inProgress")
    "unknown" -> AgentPromptTestRunnerBundle.message("context.tests.status.unknown")
    else -> status
  }
}

