// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.opencode.sessions.server

import com.intellij.openapi.application.PathManager
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val OPEN_CODE_SERVER_SYSTEM_DIR = "agent-workbench/opencode-server"

internal fun resolveOpenCodeServerWorkingDirectory(requestedWorkingDirectory: Path?): Path {
  val effectiveWorkingDirectory = requestedWorkingDirectory?.takeIf(Files::isDirectory)
  if (effectiveWorkingDirectory != null) {
    return effectiveWorkingDirectory
  }

  val fallbackWorkingDirectory = PathManager.getSystemDir().resolve(OPEN_CODE_SERVER_SYSTEM_DIR)
  Files.createDirectories(fallbackWorkingDirectory)
  return fallbackWorkingDirectory
}

internal fun stopOpenCodeServerProcess(process: Process, timeoutMs: Long, coroutineScope: CoroutineScope) {
  if (EDT.isCurrentThreadEdt()) {
    coroutineScope.launch(CoroutineName("OpenCode server process shutdown") + Dispatchers.IO) {
      terminateOpenCodeServerProcess(process, timeoutMs)
    }
  }
  else {
    terminateOpenCodeServerProcess(process, timeoutMs)
  }
}

private fun terminateOpenCodeServerProcess(process: Process, timeoutMs: Long) {
  try {
    process.destroy()
  }
  catch (_: Throwable) {
  }

  try {
    if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
      process.destroyForcibly()
      process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
    }
  }
  catch (_: Throwable) {
  }
}
