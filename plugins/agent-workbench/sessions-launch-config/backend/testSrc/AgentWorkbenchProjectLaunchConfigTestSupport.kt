// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.openapi.util.io.NioFiles
import com.intellij.platform.eel.EelOsFamily
import com.intellij.platform.eel.pathSeparator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal const val AGENT_WORKBENCH_TEST_PATH_PREPEND: String = "custom/bin"

internal data class AgentWorkbenchTestLaunchConfig(
  val pathPrepend: List<String> = emptyList(),
  val commandShims: Map<String, String> = emptyMap(),
) {
  fun isEmpty(): Boolean = pathPrepend.isEmpty() && commandShims.isEmpty()
}

internal fun testLaunchConfig(
  pathPrepend: String? = AGENT_WORKBENCH_TEST_PATH_PREPEND,
  commandShimTarget: String? = "community/tools/bun.cmd",
): AgentWorkbenchTestLaunchConfig {
  return AgentWorkbenchTestLaunchConfig(
    pathPrepend = listOfNotNull(pathPrepend),
    commandShims = buildMap {
      if (commandShimTarget != null) {
        put("bun", commandShimTarget)
      }
    },
  )
}

internal fun writeAgentWorkbenchProjectConfig(
  projectDir: Path,
  shared: AgentWorkbenchTestLaunchConfig = testLaunchConfig(),
  providers: Map<AgentSessionProvider, AgentWorkbenchTestLaunchConfig> = emptyMap(),
) {
  Files.createDirectories(projectDir)
  sequenceOf(shared, *providers.values.toTypedArray())
    .flatMap { it.pathPrepend.asSequence() }
    .forEach { Files.createDirectories(projectDir.resolve(it)) }

  val yaml = buildString {
    appendLaunchConfig(shared, indent = "")
    if (providers.isNotEmpty()) {
      appendLine("providers:")
      providers.forEach { (provider, config) ->
        appendLine("  ${provider.value}:")
        appendLaunchConfig(config, indent = "    ")
      }
    }
  }

  Files.writeString(projectDir.resolve(".agent-workbench.yaml"), yaml, StandardCharsets.UTF_8)
}

private fun StringBuilder.appendLaunchConfig(config: AgentWorkbenchTestLaunchConfig, indent: String) {
  if (config.pathPrepend.isNotEmpty()) {
    appendLine("${indent}pathPrepend:")
    config.pathPrepend.forEach { appendLine("$indent  - $it") }
  }
  if (config.commandShims.isNotEmpty()) {
    appendLine("${indent}commandShims:")
    config.commandShims.forEach { (name, target) -> appendLine("$indent  $name: $target") }
  }
}

internal fun createCommandShimTarget(path: Path, executable: Boolean = true) {
  Files.createDirectories(path.parent)
  Files.writeString(path, "#!/bin/sh\n", StandardCharsets.UTF_8)
  if (executable) {
    NioFiles.setExecutable(path)
  }
}

internal fun splitPathEntries(pathValue: String, osFamily: EelOsFamily): List<String> {
  return pathValue.split(osFamily.pathSeparator.single()).filter { it.isNotBlank() }
}

internal fun windowsEnvironment(pathValue: String): Map<String, String> {
  return linkedMapOf(
    "Path" to pathValue,
    "PATH" to pathValue,
  )
}
