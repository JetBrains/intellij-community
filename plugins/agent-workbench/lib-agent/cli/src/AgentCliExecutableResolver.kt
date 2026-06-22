// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.cli

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.jetbrains.plugins.terminal.agent.TerminalAgentResolver
import java.nio.file.Files
import java.nio.file.Path

private val LOG = logger<AgentCliExecutableResolverLogCategory>()

private class AgentCliExecutableResolverLogCategory

@ApiStatus.Internal
suspend fun resolveExecutableViaTerminalResolver(
  terminalAgent: TerminalAgent,
  binaryPathResolver: suspend (TerminalAgent) -> String? = ::resolveTerminalAgentBinaryPath,
): String? {
  return try {
    binaryPathResolver(terminalAgent)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.debug("Failed to resolve binary for terminal agent '${terminalAgent.agentKey.key}'", e)
    null
  }
}

@ApiStatus.Internal
suspend fun resolveExecutableViaTerminalResolver(
  terminalAgentKey: String,
  fallbackResolver: (() -> String?)? = null,
  missingAgentMessage: String? = null,
  terminalAgentLookup: (TerminalAgent.AgentKey) -> TerminalAgent? = TerminalAgent::findByKey,
  binaryPathResolver: suspend (TerminalAgent) -> String? = ::resolveTerminalAgentBinaryPath,
): String? {
  val terminalAgent = lookupTerminalAgent(terminalAgentKey, terminalAgentLookup)
  if (terminalAgent == null) {
    if (missingAgentMessage != null) {
      LOG.warn(missingAgentMessage)
    }
    return fallbackResolver?.invoke()
  }
  return resolveExecutableViaTerminalResolver(terminalAgent, binaryPathResolver) ?: fallbackResolver?.invoke()
}

@ApiStatus.Internal
suspend fun resolveExecutableOrDefaultViaTerminalResolver(
  defaultCommand: String,
  terminalAgent: TerminalAgent,
  binaryPathResolver: suspend (TerminalAgent) -> String? = ::resolveTerminalAgentBinaryPath,
): String {
  return resolveExecutableViaTerminalResolver(terminalAgent, binaryPathResolver) ?: defaultCommand
}

@ApiStatus.Internal
suspend fun resolveExecutableOrDefaultViaTerminalResolver(
  defaultCommand: String,
  terminalAgentKey: String,
  fallbackResolver: (() -> String?)? = null,
  missingAgentMessage: String? = null,
  terminalAgentLookup: (TerminalAgent.AgentKey) -> TerminalAgent? = TerminalAgent::findByKey,
  binaryPathResolver: suspend (TerminalAgent) -> String? = ::resolveTerminalAgentBinaryPath,
): String {
  return resolveExecutableViaTerminalResolver(
    terminalAgentKey = terminalAgentKey,
    fallbackResolver = fallbackResolver,
    missingAgentMessage = missingAgentMessage,
    terminalAgentLookup = terminalAgentLookup,
    binaryPathResolver = binaryPathResolver,
  ) ?: defaultCommand
}

@ApiStatus.Internal
fun resolveExecutableFromPath(command: String): String? {
  return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(command)?.toPath()?.toAbsolutePath()?.toString()
}

@ApiStatus.Internal
fun resolveExecutableFromPathOrLocalBin(
  command: String,
  localExecutableNames: List<String> = listOf(command),
  userHomePath: Path? = System.getProperty("user.home")?.let(Path::of),
): String? {
  resolveExecutableFromPath(command)?.let { return it }
  val homePath = userHomePath ?: return null
  for (executableName in localExecutableNames) {
    val localBin = homePath.resolve(".local").resolve("bin").resolve(executableName)
    if (Files.isRegularFile(localBin)) return localBin.toAbsolutePath().toString()
  }
  return null
}

@ApiStatus.Internal
fun terminalAgentBinaryNameOrDefault(
  terminalAgentKey: String,
  defaultCommand: String,
  terminalAgentLookup: (TerminalAgent.AgentKey) -> TerminalAgent? = TerminalAgent::findByKey,
): String {
  return lookupTerminalAgent(terminalAgentKey, terminalAgentLookup)?.binaryName ?: defaultCommand
}

private fun lookupTerminalAgent(
  terminalAgentKey: String,
  terminalAgentLookup: (TerminalAgent.AgentKey) -> TerminalAgent?,
): TerminalAgent? {
  return runCatching {
    terminalAgentLookup(TerminalAgent.AgentKey(terminalAgentKey))
  }.onFailure { error ->
    LOG.debug("Failed to resolve terminal agent for key '$terminalAgentKey'", error)
  }.getOrNull()
}

private suspend fun resolveTerminalAgentBinaryPath(terminalAgent: TerminalAgent): String? {
  val eelApi = LocalEelDescriptor.toEelApi()
  return TerminalAgentResolver.findBinaryPath(terminalAgent, eelApi)
}
