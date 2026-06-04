// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger

private val LOG = logger<JbCentralQuotaCliClient>()
private const val PROCESS_TIMEOUT_MS = 5_000
private val USAGE_RE = Regex("""Usage:\s*\$(\d+(?:\.\d+)?)\s*/\s*\$(\d+(?:\.\d+)?)(?:\s*\((\d+(?:\.\d+)?)%\))?""")
private val REMAINING_RE = Regex("""Remaining:\s*\$(\d+(?:\.\d+)?)""")
private val RESETS_RE = Regex("""(?:Resets|Expires):\s*(.+)""")

internal class JbCentralQuotaCliClient(
  private val executableResolver: () -> String? = JbCentralQuotaCliSupport::findExecutable,
) {
  fun fetchQuota(): JbCentralQuotaFetchResult {
    val executable = executableResolver() ?: return JbCentralQuotaFetchResult(error = JbCentralQuotaError.CLI_NOT_FOUND)
    return try {
      val commandLine = GeneralCommandLine(executable, "quota")
      val result = CapturingProcessHandler(commandLine).runProcess(PROCESS_TIMEOUT_MS)
      if (result.isTimeout) {
        return JbCentralQuotaFetchResult(error = JbCentralQuotaError.COMMAND_FAILED)
      }

      val stdout = result.stdout.trim()
      val stderr = result.stderr.trim()
      val combinedOutput = sequenceOf(stdout, stderr)
        .filter { it.isNotBlank() }
        .joinToString(separator = "\n")
        .trim()

      if (result.exitCode != 0) {
        return JbCentralQuotaFetchResult(error = classifyError(combinedOutput))
      }

      val quotaInfo = parseQuotaInfo(stdout.ifBlank { combinedOutput })
        ?: return JbCentralQuotaFetchResult(error = JbCentralQuotaError.PARSE_FAILED)
      JbCentralQuotaFetchResult(quotaInfo = quotaInfo)
    }
    catch (t: Throwable) {
      LOG.warn("Failed to fetch JBCentral quota", t)
      JbCentralQuotaFetchResult(error = JbCentralQuotaError.COMMAND_FAILED)
    }
  }
}

private fun classifyError(output: String): JbCentralQuotaError {
  return if (output.contains("not logged in", ignoreCase = true)) {
    JbCentralQuotaError.NOT_LOGGED_IN
  }
  else {
    JbCentralQuotaError.COMMAND_FAILED
  }
}

private fun parseQuotaInfo(output: String): JbCentralQuotaInfo? {
  var email: String? = null
  var licenseName: String? = null
  var usedUsd: String? = null
  var totalUsd: String? = null
  var remainingUsd: String? = null
  var percentUsed: Double? = null
  var resetDateText: String? = null

  for (rawLine in output.lineSequence()) {
    val line = rawLine.trim()
    if (line.isEmpty()) continue

    if (email == null && line.contains('·') && !line.startsWith("Usage:")) {
      val parts = line.split('·', limit = 2)
      if (parts.size == 2) {
        email = parts[0].trim().ifEmpty { null }
        licenseName = parts[1].trim().ifEmpty { null }
      }
      continue
    }

    val usageMatch = USAGE_RE.matchEntire(line)
    if (usageMatch != null) {
      usedUsd = usageMatch.groupValues[1]
      totalUsd = usageMatch.groupValues[2]
      percentUsed = usageMatch.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toDoubleOrNull()
      continue
    }

    val remainingMatch = REMAINING_RE.matchEntire(line)
    if (remainingMatch != null) {
      remainingUsd = remainingMatch.groupValues[1]
      continue
    }

    val resetMatch = RESETS_RE.matchEntire(line)
    if (resetMatch != null) {
      resetDateText = resetMatch.groupValues[1].trim().ifEmpty { null }
    }
  }

  val resolvedUsed = usedUsd ?: return null
  val resolvedTotal = totalUsd ?: return null
  val resolvedRemaining = remainingUsd ?: calculateRemaining(resolvedUsed, resolvedTotal)
  val resolvedPercent = percentUsed ?: calculatePercentUsed(resolvedUsed, resolvedTotal)

  return JbCentralQuotaInfo(
    email = email,
    licenseName = licenseName,
    usedUsd = resolvedUsed,
    totalUsd = resolvedTotal,
    remainingUsd = resolvedRemaining,
    percentUsed = resolvedPercent,
    resetDateText = resetDateText,
  )
}

private fun calculateRemaining(usedUsd: String, totalUsd: String): String {
  val remaining = (totalUsd.toDoubleOrNull() ?: return "0.00") - (usedUsd.toDoubleOrNull() ?: 0.0)
  return "%.2f".format(java.util.Locale.US, remaining.coerceAtLeast(0.0))
}

private fun calculatePercentUsed(usedUsd: String, totalUsd: String): Double? {
  val used = usedUsd.toDoubleOrNull() ?: return null
  val total = totalUsd.toDoubleOrNull() ?: return null
  if (total <= 0.0) return null
  return used / total * 100.0
}
