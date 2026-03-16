// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.claude.sessions

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import java.nio.file.Files
import java.nio.file.Path

object ClaudeCliSupport {
  const val CLAUDE_COMMAND: String = "claude"

  fun isAvailable(): Boolean = findExecutable() != null

  fun findExecutable(): String? {
    val inPath = PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(CLAUDE_COMMAND)
    @Suppress("IO_FILE_USAGE")
    if (inPath != null) return inPath.toPath().toAbsolutePath().toString()

    val homeDir = System.getProperty("user.home") ?: return null
    val localBin = Path.of(homeDir, ".local", "bin", CLAUDE_COMMAND)
    if (Files.exists(localBin)) return localBin.toAbsolutePath().toString()

    return null
  }

  fun buildNewSessionCommand(yolo: Boolean): List<String> =
    if (yolo) listOf(CLAUDE_COMMAND, "--dangerously-skip-permissions")
    else listOf(CLAUDE_COMMAND)

  fun buildResumeCommand(sessionId: String): List<String> =
    listOf(CLAUDE_COMMAND, "--resume", sessionId)
}

