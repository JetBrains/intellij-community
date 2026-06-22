// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.cli

import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.plugins.terminal.agent.TerminalAgent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.Icon

class AgentCliExecutableResolverTest {
  @Test
  fun directAgentResolutionUsesInjectedBinaryResolver(): Unit = runBlocking(Dispatchers.Default) {
    val agent = testTerminalAgent(key = "codex", binaryName = "codex")

    val executable = resolveExecutableViaTerminalResolver(agent) { terminalAgent ->
      "/opt/bin/${terminalAgent.binaryName}"
    }

    assertThat(executable).isEqualTo("/opt/bin/codex")
  }

  @Test
  fun keyBasedResolutionUsesRegisteredAgent(): Unit = runBlocking(Dispatchers.Default) {
    val agent = testTerminalAgent(key = "claude_code", binaryName = "claude")

    val executable = resolveExecutableViaTerminalResolver(
      terminalAgentKey = "claude_code",
      terminalAgentLookup = { key -> if (key.key == "claude_code") agent else null },
      binaryPathResolver = { terminalAgent -> "/usr/local/bin/${terminalAgent.binaryName}" },
    )

    assertThat(executable).isEqualTo("/usr/local/bin/claude")
  }

  @Test
  fun registeredAgentUsesFallbackWhenBinaryLookupFindsNoExecutable(): Unit = runBlocking(Dispatchers.Default) {
    val agent = testTerminalAgent(key = "codex", binaryName = "codex")

    val executable = resolveExecutableViaTerminalResolver(
      terminalAgentKey = "codex",
      fallbackResolver = { "/fallback/codex" },
      terminalAgentLookup = { key -> if (key.key == "codex") agent else null },
      binaryPathResolver = { null },
    )

    assertThat(executable).isEqualTo("/fallback/codex")
  }

  @Test
  fun registeredAgentUsesFallbackWhenBinaryLookupFails(): Unit = runBlocking(Dispatchers.Default) {
    val agent = testTerminalAgent(key = "codex", binaryName = "codex")

    val executable = resolveExecutableViaTerminalResolver(
      terminalAgentKey = "codex",
      fallbackResolver = { "/fallback/codex" },
      terminalAgentLookup = { key -> if (key.key == "codex") agent else null },
      binaryPathResolver = { error("broken binary lookup") },
    )

    assertThat(executable).isEqualTo("/fallback/codex")
  }

  @Test
  fun binaryLookupCancellationPropagates() {
    val agent = testTerminalAgent(key = "codex", binaryName = "codex")

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        resolveExecutableViaTerminalResolver(agent) {
          throw CancellationException("cancelled")
        }
      }
    }.isInstanceOf(CancellationException::class.java)
  }

  @Test
  fun binaryLookupProcessCancellationPropagates() {
    val agent = testTerminalAgent(key = "codex", binaryName = "codex")

    assertThatThrownBy {
      runBlocking(Dispatchers.Default) {
        resolveExecutableViaTerminalResolver(agent) {
          throw ProcessCanceledException()
        }
      }
    }.isInstanceOf(ProcessCanceledException::class.java)
  }

  @Test
  fun missingAgentUsesFallbackWithoutBinaryLookup(): Unit = runBlocking(Dispatchers.Default) {
    var binaryLookupCalled = false

    val executable = resolveExecutableViaTerminalResolver(
      terminalAgentKey = "missing",
      fallbackResolver = { "/fallback/agent" },
      terminalAgentLookup = { null },
      binaryPathResolver = {
        binaryLookupCalled = true
        "/unexpected"
      },
    )

    assertThat(executable).isEqualTo("/fallback/agent")
    assertThat(binaryLookupCalled).isFalse()
  }

  @Test
  fun lookupFailureUsesFallbackWithoutBinaryLookup(): Unit = runBlocking(Dispatchers.Default) {
    var binaryLookupCalled = false

    val executable = resolveExecutableViaTerminalResolver(
      terminalAgentKey = "broken",
      fallbackResolver = { "/fallback/agent" },
      terminalAgentLookup = { error("broken terminal agent extension") },
      binaryPathResolver = {
        binaryLookupCalled = true
        "/unexpected"
      },
    )

    assertThat(executable).isEqualTo("/fallback/agent")
    assertThat(binaryLookupCalled).isFalse()
  }

  @Test
  fun defaultCommandIsReturnedWhenResolverFindsNoExecutable(): Unit = runBlocking(Dispatchers.Default) {
    val executable = resolveExecutableOrDefaultViaTerminalResolver(
      defaultCommand = "pi",
      terminalAgent = testTerminalAgent(key = "pi", binaryName = "pi"),
      binaryPathResolver = { null },
    )

    assertThat(executable).isEqualTo("pi")
  }

  @Test
  fun terminalAgentBinaryNameFallsBackToDefault() {
    val registered = testTerminalAgent(key = "codex", binaryName = "codex-nightly")

    assertThat(
      terminalAgentBinaryNameOrDefault(
        terminalAgentKey = "codex",
        defaultCommand = "codex",
        terminalAgentLookup = { key -> if (key.key == "codex") registered else null },
      )
    ).isEqualTo("codex-nightly")
    assertThat(
      terminalAgentBinaryNameOrDefault(
        terminalAgentKey = "missing",
        defaultCommand = "codex",
        terminalAgentLookup = { null },
      )
    ).isEqualTo("codex")
    assertThat(
      terminalAgentBinaryNameOrDefault(
        terminalAgentKey = "broken",
        defaultCommand = "codex",
        terminalAgentLookup = { error("broken terminal agent extension") },
      )
    ).isEqualTo("codex")
  }

  @Test
  fun localBinFallbackResolvesHomeRelativeExecutable(@TempDir homePath: Path) {
    val executable = homePath.resolve(".local").resolve("bin").resolve("agent-test-cli")
    Files.createDirectories(executable.parent)
    Files.writeString(executable, "")

    assertThat(
      resolveExecutableFromPathOrLocalBin(
        command = "agent-test-cli-command-that-should-not-exist",
        localExecutableNames = listOf("agent-test-cli"),
        userHomePath = homePath,
      )
    ).isEqualTo(executable.toAbsolutePath().toString())
  }

  @Test
  fun localBinFallbackIgnoresNonFileCandidates(@TempDir homePath: Path) {
    val executableDirectory = homePath.resolve(".local").resolve("bin").resolve("agent-test-cli")
    Files.createDirectories(executableDirectory)

    assertThat(
      resolveExecutableFromPathOrLocalBin(
        command = "agent-test-cli-command-that-should-not-exist",
        localExecutableNames = listOf("agent-test-cli"),
        userHomePath = homePath,
      )
    ).isNull()
  }
}

private fun testTerminalAgent(
  key: String,
  binaryName: String,
): TerminalAgent {
  return object : TerminalAgent {
    override val agentKey: TerminalAgent.AgentKey = TerminalAgent.AgentKey(key)
    override val displayName: String = "Test Agent"
    override val binaryName: String = binaryName
    override val icon: Icon? = null
  }
}
