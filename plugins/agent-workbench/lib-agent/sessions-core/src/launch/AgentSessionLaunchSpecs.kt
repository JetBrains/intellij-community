// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.launch

import com.intellij.platform.ai.agent.core.session.AgentSessionProvider
import com.intellij.platform.ai.agent.sessions.core.providers.AgentSessionTerminalLaunchSpec

object AgentSessionLaunchSpecs {
  suspend fun augment(
    projectPath: String,
    projectDirectory: String? = null,
    provider: AgentSessionProvider,
    launchSpec: AgentSessionTerminalLaunchSpec,
  ): AgentSessionTerminalLaunchSpec {
    return AgentSessionLaunchSpecAugmenters.augment(
      projectPath = projectPath,
      projectDirectory = projectDirectory,
      provider = provider,
      launchSpec = launchSpec,
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
