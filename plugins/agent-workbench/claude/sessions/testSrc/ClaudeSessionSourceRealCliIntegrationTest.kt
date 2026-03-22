// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.agent.workbench.claude.sessions.backend.store.ClaudeStoreSessionBackend
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.io.path.exists
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val RUN_CLAUDE_REAL_CLI_E2E_PROPERTY = "agent.workbench.sessions.runClaudeRealCliE2e"

class ClaudeSessionSourceRealCliIntegrationTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun realPrintCommandPersistsSessionAndListsItThroughSessionSource() {
    runBlocking(Dispatchers.IO) {
      assumeTrue(
        java.lang.Boolean.getBoolean(RUN_CLAUDE_REAL_CLI_E2E_PROPERTY),
        "Set -D$RUN_CLAUDE_REAL_CLI_E2E_PROPERTY=true to run the real Claude CLI integration test",
      )

      val claudeBinary = ClaudeCliSupport.findExecutable()
      assumeTrue(claudeBinary != null, "Claude CLI not found. Ensure `claude` is on PATH.")

      val credentialsFile = findClaudeCredentialsFile()
      assumeTrue(credentialsFile != null, "No Claude credentials found in ~/.claude/.credentials.json")
      val resolvedCredentialsFile = checkNotNull(credentialsFile)

      val isolatedHome = tempDir.resolve("home")
      val claudeHome = isolatedHome.resolve(".claude")
      val isolatedCredentials = claudeHome.resolve(".credentials.json")
      Files.createDirectories(claudeHome)
      Files.copy(resolvedCredentialsFile, isolatedCredentials, StandardCopyOption.REPLACE_EXISTING)

      val projectDir = tempDir.resolve("project")
      Files.createDirectories(projectDir)

      val sessionId = UUID.randomUUID().toString()
      val prompt = "Claude real integration title ${sessionId.take(8)}"
      val processOutput = CapturingProcessHandler(
        GeneralCommandLine(
          claudeBinary,
          "--print",
          PERMISSION_MODE_FLAG,
          PERMISSION_MODE_PLAN,
          "--session-id",
          sessionId,
          "--",
          prompt,
        )
          .withWorkingDirectory(projectDir)
          .withEnvironment("HOME", isolatedHome.toString())
          .withEnvironment("DISABLE_AUTOUPDATER", "1")
      ).runProcess(180_000)

      assumeTrue(
        processOutput.exitCode == 0,
        buildString {
          appendLine("Claude CLI print command failed in isolated HOME; skipping.")
          appendLine("exitCode=${processOutput.exitCode}")
          appendLine("stdout=${processOutput.stdout.trim()}")
          appendLine("stderr=${processOutput.stderr.trim()}")
        },
      )

      val backend = ClaudeStoreSessionBackend(claudeHomeProvider = { claudeHome })
      val source = ClaudeSessionSource(backend = backend)
      val thread = awaitThread(source = source, projectPath = projectDir.toString(), sessionId = sessionId)

      assertThat(thread)
        .withFailMessage(
          "Timed out waiting for Claude session source to surface session %s in %s. stdout=%s stderr=%s",
          sessionId,
          claudeHome,
          processOutput.stdout.trim(),
          processOutput.stderr.trim(),
        )
        .isNotNull
      assertThat(thread!!.id).isEqualTo(sessionId)
      assertThat(thread.title).isEqualTo(prompt)
      assertThat(thread.provider).isEqualTo(AgentSessionProvider.CLAUDE)
    }
  }
}

private fun findClaudeCredentialsFile(): Path? {
  val userHome = System.getProperty("user.home") ?: return null
  val credentials = Path.of(userHome, ".claude", ".credentials.json")
  return credentials.takeIf { it.exists() }
}

private suspend fun awaitThread(
  source: ClaudeSessionSource,
  projectPath: String,
  sessionId: String,
): AgentSessionThread? {
  var resolvedThread: AgentSessionThread? = null
  withTimeoutOrNull(30.seconds) {
    while (resolvedThread == null) {
      resolvedThread = source.listThreadsFromClosedProject(projectPath).firstOrNull { it.id == sessionId }
      if (resolvedThread == null) {
        delay(250.milliseconds)
      }
    }
  }
  return resolvedThread
}
