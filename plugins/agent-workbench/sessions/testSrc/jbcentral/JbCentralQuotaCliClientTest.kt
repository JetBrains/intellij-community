// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.openapi.util.SystemInfoRt
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class JbCentralQuotaCliClientTest {
  @TempDir
  lateinit var tempDir: Path

  @Test
  fun parsesQuotaOutputFromConfiguredCommand() {
    val script = createStubCli(stdout = loadFixture("quota-output.txt"), exitCode = 0)

    withJbCentralPath(script) {
      val result = JbCentralQuotaCliClient().fetchQuota()

      assertThat(result.error).isNull()
      assertThat(result.quotaInfo).isEqualTo(
        JbCentralQuotaInfo(
          email = "Ivan.Kuleshov@jetbrains.com",
          licenseName = "JetBrains AI Ultimate",
          usedUsd = "1.48",
          totalUsd = "200.00",
          remainingUsd = "198.52",
          percentUsed = 0.7,
          resetDateText = "Jun 1, 2026",
        )
      )
    }
  }

  @Test
  fun reportsNotLoggedInErrorFromConfiguredCommand() {
    val script = createStubCli(stderr = loadFixture("quota-not-logged-in.txt"), exitCode = 1)

    withJbCentralPath(script) {
      val result = JbCentralQuotaCliClient().fetchQuota()

      assertThat(result.quotaInfo).isNull()
      assertThat(result.error).isEqualTo(JbCentralQuotaError.NOT_LOGGED_IN)
    }
  }

  private fun loadFixture(name: String): String {
    val resourcePath = "jbcentral/$name"
    return checkNotNull(javaClass.classLoader.getResource(resourcePath)) {
      "Missing fixture resource: $resourcePath"
    }.readText()
  }

  private fun withJbCentralPath(path: Path, action: () -> Unit) {
    val previous = System.getProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)
    System.setProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY, path.toAbsolutePath().toString())
    try {
      action()
    }
    finally {
      if (previous == null) {
        System.clearProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY)
      }
      else {
        System.setProperty(AGENT_WORKBENCH_JBCENTRAL_PATH_PROPERTY, previous)
      }
    }
  }

  private fun createStubCli(
    stdout: String = "",
    stderr: String = "",
    exitCode: Int,
  ): Path {
    return if (SystemInfoRt.isWindows) {
      tempDir.resolve("jbcentral.cmd").apply {
        writeText(
          buildString {
            appendLine("@echo off")
            if (stdout.isNotBlank()) {
              stdout.lines().forEach { appendLine("echo ${escapeWindows(it)}") }
            }
            if (stderr.isNotBlank()) {
              stderr.lines().forEach { appendLine("echo ${escapeWindows(it)} 1>&2") }
            }
            appendLine("exit /b $exitCode")
          }
        )
      }
    }
    else {
      tempDir.resolve("jbcentral").apply {
        writeText(
          buildString {
            appendLine("#!/bin/sh")
            if (stdout.isNotBlank()) {
              appendLine("cat <<'EOF'")
              append(stdout)
              if (!stdout.endsWith("\n")) appendLine()
              appendLine("EOF")
            }
            if (stderr.isNotBlank()) {
              appendLine("cat >&2 <<'EOF'")
              append(stderr)
              if (!stderr.endsWith("\n")) appendLine()
              appendLine("EOF")
            }
            appendLine("exit $exitCode")
          }
        )
        toFile().setExecutable(true)
      }
    }
  }

  private fun escapeWindows(line: String): String {
    return line
      .replace("^", "^^")
      .replace("&", "^&")
      .replace("|", "^|")
      .replace("<", "^<")
      .replace(">", "^>")
      .replace("%", "%%")
  }
}
