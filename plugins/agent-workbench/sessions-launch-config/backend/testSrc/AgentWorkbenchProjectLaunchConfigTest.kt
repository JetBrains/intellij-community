// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.launch.config.backend

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionTerminalLaunchSpec
import com.intellij.platform.eel.EelOsFamily
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

@TestApplication
class AgentWorkbenchProjectLaunchConfigTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun augmentPrependsSharedCommandShimsConfiguredPathsAndTargetPath() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(projectDir)
    createCommandShimTarget(projectDir.resolve("community/tools/bun.cmd"))
    val targetPathEntries = listOf(
      tempDir.resolve("target-path-1"),
      tempDir.resolve("target-path-2"),
    )
    targetPathEntries.forEach(Files::createDirectories)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to targetPathEntries.joinToString(":")),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(
        command = listOf("codex", "resume", "thread-1"),
        envVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
      ),
    )

    assertThat(launchSpec.command).containsExactly("codex", "resume", "thread-1")
    assertThat(launchSpec.envVariables).containsEntry("DISABLE_AUTOUPDATER", "1")

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    val shimDirectory = Path.of(pathEntries.first())
    assertThat(shimDirectory.startsWith(tempDir.resolve("system").resolve("agent-workbench").resolve("command-shims").resolve("codex"))).isTrue()
    assertThat(Files.isRegularFile(shimDirectory.resolve("bun"))).isTrue()
    assertThat(pathEntries[1]).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
    assertThat(pathEntries.drop(2)).containsExactlyElementsOf(targetPathEntries.map(Path::toString))
  }

  @Test
  fun augmentUsesLaunchSpecPathWhenPresent() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(
      projectDir,
      shared = testLaunchConfig(commandShimTarget = null),
    )
    val providerPath = tempDir.resolve("provider-only-path")
    Files.createDirectories(providerPath)
    val targetPath = tempDir.resolve("target-only-path")
    Files.createDirectories(targetPath)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to targetPath.toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(
        command = listOf("codex", "resume", "thread-1"),
        envVariables = mapOf("PATH" to providerPath.toString()),
      ),
    )

    assertThat(splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix))
      .containsExactly(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString(), providerPath.toString())
  }

  @Test
  fun augmentFallsBackToOriginalLaunchSpecWhenProvidersSectionIsInvalid() {
    val projectDir = tempDir.resolve("project")
    Files.createDirectories(projectDir)
    Files.writeString(
      projectDir.resolve(".agent-workbench.yaml"),
      "providers: []\n",
      StandardCharsets.UTF_8,
    )
    val launchSpec = AgentSessionTerminalLaunchSpec(
      command = listOf("codex", "resume", "thread-1"),
      envVariables = mapOf("DISABLE_AUTOUPDATER" to "1"),
    )

    assertThat(
      augmenter(
        osFamily = EelOsFamily.Posix,
        environmentVariables = mapOf("PATH" to tempDir.resolve("base-path").toString()),
      ).augmentBlocking(
        projectPath = projectDir.toString(),
        provider = AgentSessionProvider.CODEX,
        launchSpec = launchSpec,
      )
    ).isEqualTo(launchSpec)
  }

  @Test
  fun augmentSkipsDirectoryCommandShimTarget() {
    val projectDir = tempDir.resolve("project")
    val invalidTargetDirectory = projectDir.resolve("community/tools")
    Files.createDirectories(invalidTargetDirectory)
    writeAgentWorkbenchProjectConfig(
      projectDir,
      shared = testLaunchConfig(commandShimTarget = "community/tools"),
    )

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to tempDir.resolve("base-path").toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    assertThat(pathEntries.first()).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
  }

  @Test
  fun augmentSkipsNonExecutablePosixCommandShimTarget() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(projectDir)
    val targetPath = projectDir.resolve("community/tools/bun.cmd")
    Files.createDirectories(targetPath.parent)
    Files.writeString(targetPath, "#!/bin/sh\n", StandardCharsets.UTF_8)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to tempDir.resolve("base-path").toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    assertThat(pathEntries.first()).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
  }

  @Test
  fun augmentGeneratesWindowsCommandShimAndUsesWindowsPathKey() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(projectDir)
    createCommandShimTarget(projectDir.resolve("community/tools/bun.cmd"))
    val targetPathValue = "C:\\Tools;C:\\System32"

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Windows,
      environmentVariables = windowsEnvironment(targetPathValue),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CLAUDE,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("claude", "resume", "thread-1")),
    )

    assertThat(launchSpec.envVariables).containsKey("Path")
    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("Path"), EelOsFamily.Windows)
    val shimDirectory = Path.of(pathEntries.first())
    assertThat(Files.isRegularFile(shimDirectory.resolve("bun.cmd"))).isTrue()
    assertThat(pathEntries[1]).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
    assertThat(pathEntries.drop(2)).containsExactly("C:\\Tools", "C:\\System32")
  }

  @Test
  fun augmentUsesSharedConfigWhenProvidersSectionIsInvalid() {
    val projectDir = tempDir.resolve("project")
    Files.createDirectories(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND))
    Files.writeString(
      projectDir.resolve(".agent-workbench.yaml"),
      """
      pathPrepend:
        - $AGENT_WORKBENCH_TEST_PATH_PREPEND
      commandShims:
        bun: community/tools/bun.cmd
      providers: []
      """.trimIndent() + "\n",
      StandardCharsets.UTF_8,
    )
    createCommandShimTarget(projectDir.resolve("community/tools/bun.cmd"))

    val basePath = tempDir.resolve("base-path")
    Files.createDirectories(basePath)
    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to basePath.toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    assertThat(Files.isRegularFile(Path.of(pathEntries.first()).resolve("bun"))).isTrue()
    assertThat(pathEntries[1]).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
    assertThat(pathEntries.drop(2)).containsExactly(basePath.toString())
  }

  @Test
  fun augmentMergesProviderOverridesOverSharedConfig() {
    val projectDir = tempDir.resolve("project")
    val providerPathPrepend = "claude/bin"
    val providerCommandShimTarget = "community/tools/bun-claude.cmd"
    writeAgentWorkbenchProjectConfig(
      projectDir,
      shared = testLaunchConfig(),
      providers = mapOf(
        AgentSessionProvider.CLAUDE to testLaunchConfig(
          pathPrepend = providerPathPrepend,
          commandShimTarget = providerCommandShimTarget,
        )
      ),
    )
    createCommandShimTarget(projectDir.resolve("community/tools/bun.cmd"))
    val providerShimTargetPath = projectDir.resolve(providerCommandShimTarget)
    createCommandShimTarget(providerShimTargetPath)
    val basePath = tempDir.resolve("base-path")
    Files.createDirectories(basePath)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to basePath.toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CLAUDE,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("claude", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    val shimDirectory = Path.of(pathEntries.first())
    assertThat(Files.isRegularFile(shimDirectory.resolve("bun"))).isTrue()
    assertThat(pathEntries[1]).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
    assertThat(pathEntries[2]).isEqualTo(projectDir.resolve(providerPathPrepend).toString())
    assertThat(pathEntries.drop(3)).containsExactly(basePath.toString())
    assertThat(Files.readString(shimDirectory.resolve("bun"), StandardCharsets.UTF_8)).contains(providerShimTargetPath.toString())
  }

  @Test
  fun augmentIgnoresInvalidSharedEntriesAndKeepsProviderOverrides() {
    val projectDir = tempDir.resolve("project")
    Files.createDirectories(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND))
    Files.writeString(
      projectDir.resolve(".agent-workbench.yaml"),
      """
      pathPrepend: {}
      commandShims: []
      providers:
        codex:
          pathPrepend:
            - $AGENT_WORKBENCH_TEST_PATH_PREPEND
          commandShims:
            bun: community/tools/bun.cmd
      """.trimIndent() + "\n",
      StandardCharsets.UTF_8,
    )
    createCommandShimTarget(projectDir.resolve("community/tools/bun.cmd"))
    val basePath = tempDir.resolve("base-path")
    Files.createDirectories(basePath)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to basePath.toString()),
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    assertThat(Files.isRegularFile(Path.of(pathEntries.first()).resolve("bun"))).isTrue()
    assertThat(pathEntries[1]).isEqualTo(projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND).toString())
    assertThat(pathEntries.drop(2)).containsExactly(basePath.toString())
  }

  @Test
  fun augmentUsesCachedProjectConfigForServiceLifetime() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(
      projectDir = projectDir,
      shared = testLaunchConfig(
        commandShimTarget = null,
        pathPrepend = "old/bin",
      ),
    )
    val cache = AgentWorkbenchProjectLaunchConfigCache()
    val augmenter = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = emptyMap(),
      projectConfigCache = cache,
    )
    val baseLaunchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1"))

    val firstLaunchSpec = augmenter.augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = baseLaunchSpec,
    )

    writeAgentWorkbenchProjectConfig(
      projectDir = projectDir,
      shared = testLaunchConfig(
        commandShimTarget = null,
        pathPrepend = "new/bin",
      ),
    )

    val secondLaunchSpec = augmenter.augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = baseLaunchSpec,
    )

    assertThat(splitPathEntries(firstLaunchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix))
      .containsExactly(projectDir.resolve("old/bin").toString())
    assertThat(splitPathEntries(secondLaunchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix))
      .containsExactly(projectDir.resolve("old/bin").toString())
  }

  @Test
  fun augmentUsesTargetNativePathsForPathAndCommandShims() {
    val projectDir = tempDir.resolve("project")
    writeAgentWorkbenchProjectConfig(projectDir)
    val commandShimTarget = projectDir.resolve("community/tools/bun.cmd")
    createCommandShimTarget(commandShimTarget)

    val launchSpec = augmenter(
      osFamily = EelOsFamily.Posix,
      environmentVariables = mapOf("PATH" to "/usr/bin:/bin"),
      targetPathStringResolver = { path ->
        when {
          path == tempDir.resolve("system") -> "/remote/system"
          path.startsWith(tempDir.resolve("system")) -> "/remote/system/${tempDir.resolve("system").relativize(path).invariantSeparatorsPathString}"
          path == commandShimTarget -> "/remote/project/community/tools/bun.cmd"
          path == projectDir.resolve(AGENT_WORKBENCH_TEST_PATH_PREPEND) -> "/remote/project/$AGENT_WORKBENCH_TEST_PATH_PREPEND"
          else -> path.toString()
        }
      },
    ).augmentBlocking(
      projectPath = projectDir.toString(),
      provider = AgentSessionProvider.CODEX,
      launchSpec = AgentSessionTerminalLaunchSpec(command = listOf("codex", "resume", "thread-1")),
    )

    val pathEntries = splitPathEntries(launchSpec.envVariables.getValue("PATH"), EelOsFamily.Posix)
    assertThat(pathEntries.first()).startsWith("/remote/system/agent-workbench/command-shims/codex/")
    assertThat(pathEntries[1]).isEqualTo("/remote/project/$AGENT_WORKBENCH_TEST_PATH_PREPEND")
    assertThat(pathEntries.drop(2)).containsExactly("/usr/bin", "/bin")

    val shimDirectory = tempDir.resolve("system").resolve("agent-workbench").resolve("command-shims").resolve("codex")
    val shimFile = Files.walk(shimDirectory).use { paths -> paths.filter(Files::isRegularFile).findFirst().orElseThrow() }
    assertThat(Files.readString(shimFile, StandardCharsets.UTF_8)).contains("/remote/project/community/tools/bun.cmd")
  }

  private fun augmenter(
    osFamily: EelOsFamily,
    environmentVariables: Map<String, String>,
    projectConfigCache: AgentWorkbenchProjectLaunchConfigCache = AgentWorkbenchProjectLaunchConfigCache(),
    targetPathStringResolver: (Path) -> String = Path::toString,
  ): AgentWorkbenchProjectLaunchConfigAugmenter {
    return AgentWorkbenchProjectLaunchConfigAugmenter(
      executionContextResolver = AgentWorkbenchLaunchExecutionContextResolver { projectPath ->
        val projectRoot = Path.of(projectPath)
        AgentWorkbenchLaunchExecutionContext(
          projectRoot = projectRoot,
          systemDir = tempDir.resolve("system"),
          osFamily = osFamily,
          environmentVariables = environmentVariables,
          targetPathStringResolver = targetPathStringResolver,
        )
      },
      projectConfigCache = projectConfigCache,
    )
  }
}

private fun AgentWorkbenchProjectLaunchConfigAugmenter.augmentBlocking(
  projectPath: String,
  provider: AgentSessionProvider,
  launchSpec: AgentSessionTerminalLaunchSpec,
): AgentSessionTerminalLaunchSpec {
  return runBlocking(Dispatchers.Default) {
    augment(projectPath = projectPath, provider = provider, launchSpec = launchSpec)
  }
}
