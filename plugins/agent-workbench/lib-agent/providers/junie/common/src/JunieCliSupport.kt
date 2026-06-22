// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("TestOnlyProblems")

package com.intellij.platform.ai.agent.junie.common

import com.intellij.platform.ai.agent.cli.resolveExecutableFromPathOrLocalBin
import com.intellij.platform.ai.agent.cli.resolveExecutableOrDefaultViaTerminalResolver as resolveCliExecutableOrDefaultViaTerminalResolver
import com.intellij.platform.ai.agent.cli.resolveExecutableViaTerminalResolver as findCliExecutableViaTerminalResolver
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.platform.eel.EelProcess
import com.intellij.platform.eel.ExecuteProcessException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.provider.utils.awaitProcessResult
import com.intellij.platform.eel.spawnProcess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

private val LOG = logger<JunieCliSupportLogCategory>()

private class JunieCliSupportLogCategory

private const val JUNIE_MISSING_TERMINAL_AGENT_MESSAGE: String =
  "Junie terminal agent extension is not registered; falling back to local PATH lookup"

object JunieCliSupport {
  const val JUNIE_COMMAND: String = "junie"
  const val JUNIE_TERMINAL_AGENT_KEY: String = "junie"

  fun isAvailable(): Boolean = findExecutable() != null

  fun findExecutable(): String? {
    return resolveExecutableFromPathOrLocalBin(JUNIE_COMMAND, localExecutableNames())
  }

  suspend fun findExecutableViaTerminalResolver(): String? {
    return findCliExecutableViaTerminalResolver(
      terminalAgentKey = JUNIE_TERMINAL_AGENT_KEY,
      fallbackResolver = ::findExecutable,
      missingAgentMessage = JUNIE_MISSING_TERMINAL_AGENT_MESSAGE,
    )
  }

  suspend fun resolveCliInfoViaTerminalResolver(): JunieCliInfo? {
    val executable = findExecutableViaTerminalResolver() ?: return null
    return JunieCliInfo(
      executable = executable,
      version = queryCliVersion(executable),
    )
  }

  suspend fun resolveExecutableOrDefaultViaTerminalResolver(): String =
    resolveCliExecutableOrDefaultViaTerminalResolver(
      defaultCommand = JUNIE_COMMAND,
      terminalAgentKey = JUNIE_TERMINAL_AGENT_KEY,
      fallbackResolver = ::findExecutable,
      missingAgentMessage = JUNIE_MISSING_TERMINAL_AGENT_MESSAGE,
    )

  fun buildNewSessionCommand(yolo: Boolean = false, executable: String = JUNIE_COMMAND): List<String> {
    return if (yolo) listOf(executable, SKIP_UPDATE_CHECK_FLAG, BRAVE_FLAG)
    else listOf(executable, SKIP_UPDATE_CHECK_FLAG)
  }

  fun buildResumeCommand(
    sessionId: String,
    yolo: Boolean = false,
    executable: String = JUNIE_COMMAND,
  ): List<String> {
    return if (yolo) listOf(executable, SKIP_UPDATE_CHECK_FLAG, BRAVE_FLAG, SESSION_ID_FLAG, sessionId)
    else listOf(executable, SKIP_UPDATE_CHECK_FLAG, SESSION_ID_FLAG, sessionId)
  }

  fun buildLaunchCommandWithInitialMessage(
    baseCommand: List<String>,
    message: String,
    plan: Boolean,
  ): List<String> {
    return buildList {
      addAll(baseCommand)
      if (plan) {
        add(PLAN_FLAG)
      }
      add(PROMPT_FLAG)
      add(message)
    }
  }

  fun parseCliVersion(output: String): JunieCliVersion? {
    return JUNIE_BUILD_VERSION_REGEX.find(output)?.toJunieCliVersion()
           ?: JUNIE_PAREN_BUILD_VERSION_REGEX.find(output)?.toJunieCliVersion()
  }

  private suspend fun queryCliVersion(executable: String): JunieCliVersion? {
    var process: EelProcess? = null
    var versionProbeFailed = false
    try {
      val result = withTimeoutOrNull(JUNIE_VERSION_TIMEOUT) {
        try {
          val eelApi = LocalEelDescriptor.toEelApi()
          process = eelApi.exec.spawnProcess(executable).args(VERSION_FLAG).eelIt()
          process.awaitProcessResult()
        }
        catch (e: ExecuteProcessException) {
          versionProbeFailed = true
          LOG.debug("Failed to query Junie CLI version for $executable", e)
          null
        }
      }
      if (result == null) {
        if (!versionProbeFailed) {
          process?.kill()
          LOG.debug("Timed out while querying Junie CLI version for $executable")
        }
        return null
      }
      if (result.exitCode != 0) {
        LOG.debug("Junie CLI version probe exited with ${result.exitCode} for $executable")
        return null
      }
      return parseCliVersion(String(result.stdout, Charsets.UTF_8) + "\n" + String(result.stderr, Charsets.UTF_8))
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.debug("Failed to query Junie CLI version for $executable", e)
      return null
    }
  }

}

data class JunieCliInfo(
  val executable: String,
  val version: JunieCliVersion? = null,
) {
  val supportsInteractivePromptLaunch: Boolean
    get() = version?.let { it >= JUNIE_INTERACTIVE_PROMPT_VERSION } == true
}

data class JunieCliVersion(
  val major: Int,
  val minor: Int,
) : Comparable<JunieCliVersion> {
  override fun compareTo(other: JunieCliVersion): Int {
    val majorComparison = major.compareTo(other.major)
    return if (majorComparison != 0) majorComparison else minor.compareTo(other.minor)
  }
}

private fun MatchResult.toJunieCliVersion(): JunieCliVersion? {
  val major = groupValues.getOrNull(1)?.toIntOrNull() ?: return null
  val minor = groupValues.getOrNull(2)?.toIntOrNull() ?: return null
  return JunieCliVersion(major, minor)
}

private fun localExecutableNames(): List<String> {
  return if (SystemInfoRt.isWindows) listOf("junie.bat") else listOf(JunieCliSupport.JUNIE_COMMAND)
}

private val JUNIE_INTERACTIVE_PROMPT_VERSION: JunieCliVersion = JunieCliVersion(1963, 1)
private val JUNIE_BUILD_VERSION_REGEX: Regex = Regex("""\bbuild\s+(\d+)\.(\d+)\b""", RegexOption.IGNORE_CASE)
private val JUNIE_PAREN_BUILD_VERSION_REGEX: Regex = Regex("""\((\d+)\.(\d+)\)""")

private const val SKIP_UPDATE_CHECK_FLAG: String = "--skip-update-check"
private const val VERSION_FLAG: String = "--version"
private const val PLAN_FLAG: String = "--plan"
private const val PROMPT_FLAG: String = "--prompt"
private val JUNIE_VERSION_TIMEOUT = 3.seconds
const val BRAVE_FLAG: String = "--brave"
private const val SESSION_ID_FLAG: String = "--session-id"
