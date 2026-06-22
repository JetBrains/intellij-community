// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.core.session.AgentSessionLaunchMode
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionProviders
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.openapi.diagnostic.logger

private class AgentSessionLaunchSpecsLogCategory

private val LOG = logger<AgentSessionLaunchSpecsLogCategory>()

object AgentSessionLaunchSpecs {
  suspend fun augment(
    projectPath: String,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionLaunchSpecAugmenters.augment(projectPath = projectPath, provider = provider, launchSpec = launchSpec)
  }

  suspend fun resolveResume(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String,
    launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
  ): AgentSessionTerminalLaunchSpec {
    return resolveResume(
      projectPath = projectPath,
      provider = provider,
      sessionId = sessionId,
      launchMode = launchMode,
      baseLaunchSpecProvider = ::buildDefaultResumeLaunchSpec,
    )
  }

  suspend fun resolveResume(
    projectPath: String,
    provider: AgentSessionProvider,
    sessionId: String,
    launchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD,
    baseLaunchSpecProvider: suspend (AgentSessionProvider, String, AgentSessionLaunchMode) -> AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    val baseLaunchSpec = runCatching {
      baseLaunchSpecProvider(provider, sessionId, launchMode)
    }.getOrElse { t ->
      LOG.warn(
        "Failed to build base resume launch spec for ${provider.value}:$sessionId; falling back to default command",
        t,
      )
      fallbackResumeLaunchSpec(provider, sessionId)
    }
    val augmented = augment(projectPath = projectPath, provider = provider, launchSpec = baseLaunchSpec)
    return AgentSessionLaunchContributors.applyAll(
      projectPath = projectPath,
      provider = provider,
      sessionId = sessionId,
      launchSpec = augmented,
    )
  }
}

fun replaceOrAddOption(
  command: List<String>,
  option: String,
  value: String,
  beforeTokens: Set<String> = setOf("--"),
): List<String> {
  val boundaryIndex = findBoundaryIndex(command, beforeTokens)
  for (index in 0..<boundaryIndex) {
    if (command[index] != option) continue

    val valueIndex = index + 1
    if (valueIndex < boundaryIndex) {
      if (command[valueIndex] == value) return command
      return command.toMutableList().apply {
        this[valueIndex] = value
      }
    }

    return command.toMutableList().apply {
      add(valueIndex, value)
    }
  }

  return insertArgumentsBefore(command, listOf(option, value), beforeTokens)
}

fun removeOptions(
  command: List<String>,
  options: Set<String>,
  beforeTokens: Set<String> = emptySet(),
): List<String> {
  val boundaryIndex = findBoundaryIndex(command, beforeTokens)
  val result = mutableListOf<String>()
  var changed = false
  var index = 0
  while (index < boundaryIndex) {
    val token = command[index]
    if (token in options) {
      changed = true
      index += if (index + 1 < boundaryIndex) 2 else 1
    }
    else {
      result.add(token)
      index++
    }
  }

  if (!changed) return command

  result.addAll(command.subList(boundaryIndex, command.size))
  return result
}

fun insertArgumentsBefore(
  command: List<String>,
  arguments: List<String>,
  beforeTokens: Set<String>,
): List<String> {
  if (arguments.isEmpty()) return command

  val boundaryIndex = findBoundaryIndex(command, beforeTokens)
  return command.toMutableList().apply {
    addAll(boundaryIndex, arguments)
  }
}

private fun findBoundaryIndex(command: List<String>, beforeTokens: Set<String>): Int {
  if (beforeTokens.isEmpty()) return command.size
  return command.indexOfFirst { token -> token in beforeTokens }.takeIf { index -> index >= 0 } ?: command.size
}

private suspend fun buildDefaultResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
  launchMode: AgentSessionLaunchMode,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionProviders.find(provider)?.buildResumeLaunchSpec(sessionId, launchMode)
         ?: fallbackResumeLaunchSpec(provider, sessionId)
}

private fun fallbackResumeLaunchSpec(
  provider: AgentSessionProvider,
  sessionId: String,
): AgentSessionTerminalLaunchSpec {
  return AgentSessionTerminalLaunchSpec(command = listOf(provider.value, "resume", sessionId))
}
