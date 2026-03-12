// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.expandPathEnvVar
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.pathSeparator
import com.intellij.util.system.LowLevelLocalMachineAccess
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest

private const val PATH_ENVIRONMENT_VARIABLE: String = "PATH"
private const val WINDOWS_PATH_ENVIRONMENT_VARIABLE: String = "Path"

internal fun buildAugmentedLaunchEnvironment(
  baseEnvVariables: Map<String, String>,
  targetEnvironmentVariables: Map<String, String>,
  systemDir: Path,
  osFamily: EelOsFamily,
  provider: AgentSessionProvider,
  config: AgentWorkbenchLaunchConfig,
  targetPathStringResolver: (Path) -> String,
): Map<String, String> {
  val shimDirectory = ensureCommandShimDirectory(
    systemDir = systemDir,
    osFamily = osFamily,
    provider = provider,
    config = config,
    targetPathStringResolver = targetPathStringResolver,
  )
  return buildLaunchEnvironment(
    baseEnvVariables = baseEnvVariables,
    targetEnvironmentVariables = targetEnvironmentVariables,
    osFamily = osFamily,
    config = config,
    shimDirectory = shimDirectory,
    targetPathStringResolver = targetPathStringResolver,
  )
}

private fun ensureCommandShimDirectory(
  systemDir: Path,
  osFamily: EelOsFamily,
  provider: AgentSessionProvider,
  config: AgentWorkbenchLaunchConfig,
  targetPathStringResolver: (Path) -> String,
): Path? {
  if (config.commandShims.isEmpty()) {
    return null
  }

  val commandShims = LinkedHashMap<String, Path>()
  for ((commandName, targetPath) in config.commandShims) {
    if (!isValidCommandShimName(commandName)) {
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn("Ignoring Agent Workbench command shim '$commandName': invalid command name")
      continue
    }
    val validationError = validateCommandShimTarget(targetPath, osFamily)
    if (validationError != null) {
      AGENT_WORKBENCH_PROJECT_LAUNCH_CONFIG_LOG.warn(
        "Ignoring Agent Workbench command shim '$commandName': $validationError: $targetPath"
      )
      continue
    }
    commandShims.putIfAbsent(commandName, targetPath)
  }
  if (commandShims.isEmpty()) {
    return null
  }

  val shimDirectory = systemDir
    .resolve("agent-workbench")
    .resolve("command-shims")
    .resolve(provider.value)
    .resolve(
      fingerprintLaunchConfig(
        config = config,
        commandShims = commandShims,
        osFamily = osFamily,
        targetPathStringResolver = targetPathStringResolver,
      )
    )
  Files.createDirectories(shimDirectory)

  for ((commandName, targetPath) in commandShims) {
    val shimPath = shimDirectory.resolve(commandShimFileName(commandName, osFamily))
    val content = buildCommandShimContent(targetPath, osFamily, targetPathStringResolver)
    writeFileIfChanged(shimPath, content)
    if (!osFamily.isWindows) {
      NioFiles.setExecutable(shimPath)
    }
  }
  return shimDirectory
}

private fun buildLaunchEnvironment(
  baseEnvVariables: Map<String, String>,
  targetEnvironmentVariables: Map<String, String>,
  osFamily: EelOsFamily,
  config: AgentWorkbenchLaunchConfig,
  shimDirectory: Path?,
  targetPathStringResolver: (Path) -> String,
): Map<String, String> {
  if (shimDirectory == null && config.pathPrepend.isEmpty()) {
    return baseEnvVariables
  }

  val resolvedPathEntries = LinkedHashSet<String>()
  shimDirectory?.let { resolvedPathEntries.add(targetPathStringResolver(it)) }
  config.pathPrepend.mapTo(resolvedPathEntries, targetPathStringResolver)
  val basePathValue = findPathEnvironmentVariableValue(baseEnvVariables) ?: osFamily.expandPathEnvVar(targetEnvironmentVariables)
  if (!basePathValue.isNullOrBlank()) {
    basePathValue
      .split(osFamily.pathSeparator.single())
      .filter { it.isNotBlank() }
      .forEach(resolvedPathEntries::add)
  }

  val result = LinkedHashMap<String, String>()
  result.putAll(baseEnvVariables.filterKeys { !isPathEnvironmentVariable(it) })
  result[resolvePathEnvironmentVariableKey(baseEnvVariables, osFamily)] = resolvedPathEntries.joinToString(osFamily.pathSeparator)
  return result
}

private fun validateCommandShimTarget(targetPath: Path, osFamily: EelOsFamily): String? {
  return when {
    !Files.exists(targetPath) -> "target does not exist"
    !Files.isRegularFile(targetPath) -> "target is not a regular file"
    !osFamily.isWindows && !Files.isExecutable(targetPath) -> "target is not executable"
    else -> null
  }
}

private fun isPathEnvironmentVariable(name: String): Boolean {
  return name.equals(PATH_ENVIRONMENT_VARIABLE, ignoreCase = true)
}

private fun findPathEnvironmentVariableValue(envVariables: Map<String, String>): String? {
  return envVariables.entries.firstOrNull { (name, _) -> isPathEnvironmentVariable(name) }?.value
}

private fun resolvePathEnvironmentVariableKey(baseEnvVariables: Map<String, String>, osFamily: EelOsFamily): String {
  return baseEnvVariables.keys.firstOrNull(::isPathEnvironmentVariable)
         ?: if (osFamily.isWindows) WINDOWS_PATH_ENVIRONMENT_VARIABLE else PATH_ENVIRONMENT_VARIABLE
}

@OptIn(LowLevelLocalMachineAccess::class)
private fun writeFileIfChanged(path: Path, content: String) {
  val existingContent = try {
    Files.readString(path, StandardCharsets.UTF_8)
  }
  catch (_: NoSuchFileException) {
    null
  }
  if (existingContent == content) {
    return
  }
  Files.writeString(path, content, StandardCharsets.UTF_8)
}

private fun buildCommandShimContent(
  targetPath: Path,
  osFamily: EelOsFamily,
  targetPathStringResolver: (Path) -> String,
): String {
  val targetPathString = targetPathStringResolver(targetPath)
  return if (osFamily.isWindows) {
    """
    @echo off
    call "${quoteForBatch(targetPathString)}" %*
    exit /B %ERRORLEVEL%
    """.trimIndent()
  }
  else {
    """
    #!/bin/sh
    exec ${quoteForShell(targetPathString)} "$@"
    """.trimIndent()
  }
}

private fun commandShimFileName(commandName: String, osFamily: EelOsFamily): String {
  return if (osFamily.isWindows && !commandName.endsWith(".cmd", ignoreCase = true)) "$commandName.cmd" else commandName
}

private fun isValidCommandShimName(commandName: String): Boolean {
  return commandName.isNotBlank() && commandName.none { it == '/' || it == '\\' }
}

private fun fingerprintLaunchConfig(
  config: AgentWorkbenchLaunchConfig,
  commandShims: Map<String, Path>,
  osFamily: EelOsFamily,
  targetPathStringResolver: (Path) -> String,
): String {
  val text = buildString {
    append("O=").append(osFamily).append('\n')
    config.pathPrepend.forEach { append("P=").append(targetPathStringResolver(it)).append('\n') }
    commandShims.entries.sortedBy { it.key }.forEach { (name, path) ->
      append("C=").append(name).append('=').append(targetPathStringResolver(path)).append('\n')
    }
  }
  return MessageDigest.getInstance("SHA-256")
    .digest(text.encodeToByteArray())
    .joinToString(separator = "") { byte -> "%02x".format(byte) }
    .take(16)
}

private fun quoteForShell(value: String): String {
  return "'" + value.replace("'", "'\"'\"'") + "'"
}

private fun quoteForBatch(value: String): String {
  return value.replace("\"", "\"\"")
}
