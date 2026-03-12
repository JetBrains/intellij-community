// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecAugmenter
import com.intellij.agent.workbench.sessions.core.launch.AgentSessionLaunchSpecAugmenters
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import java.io.File
import java.nio.file.Path

internal const val AGENT_WORKBENCH_TEST_ENV_NAME: String = "AGENT_WORKBENCH_TEST_FLAG"
internal const val AGENT_WORKBENCH_TEST_ENV_VALUE: String = "enabled"
internal val AGENT_WORKBENCH_TEST_PATH_PREPEND: String = Path.of("custom", "bin").toString()

internal suspend fun <T> withTestLaunchSpecAugmenter(action: suspend () -> T): T {
  return AgentSessionLaunchSpecAugmenters.withAugmenterForTest(
    AgentSessionLaunchSpecAugmenter { _, _, launchSpec ->
      buildAugmentedLaunchSpec(launchSpec)
    },
    action,
  )
}

private fun buildAugmentedLaunchSpec(launchSpec: AgentSessionTerminalLaunchSpec): AgentSessionTerminalLaunchSpec {
  val envVariables = LinkedHashMap<String, String>()
  envVariables.putAll(launchSpec.envVariables)
  envVariables[AGENT_WORKBENCH_TEST_ENV_NAME] = AGENT_WORKBENCH_TEST_ENV_VALUE
  val basePath = launchSpec.envVariables["PATH"]
  envVariables["PATH"] = listOfNotNull(AGENT_WORKBENCH_TEST_PATH_PREPEND, basePath)
    .joinToString(File.pathSeparator)
  return launchSpec.copy(envVariables = envVariables)
}
