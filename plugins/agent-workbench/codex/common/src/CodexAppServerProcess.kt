// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.common

import com.intellij.openapi.application.PathManager
import com.intellij.util.ui.EDT
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val CODEX_APP_SERVER_SYSTEM_DIR = "agent-workbench/codex-app-server"

internal fun resolveCodexAppServerWorkingDirectory(requestedWorkingDirectory: Path?): Path {
  val effectiveWorkingDirectory = requestedWorkingDirectory?.takeIf(Files::isDirectory)
  if (effectiveWorkingDirectory != null) {
    return effectiveWorkingDirectory
  }

  val fallbackWorkingDirectory = PathManager.getSystemDir().resolve(CODEX_APP_SERVER_SYSTEM_DIR)
  Files.createDirectories(fallbackWorkingDirectory)
  return fallbackWorkingDirectory
}

internal fun stopCodexAppServerProcess(process: Process, timeoutMs: Long, coroutineScope: CoroutineScope) {
  if (EDT.isCurrentThreadEdt()) {
    coroutineScope.launch(CoroutineName("Codex app-server process shutdown") + Dispatchers.IO) {
      terminateCodexAppServerProcess(process, timeoutMs)
    }
  }
  else {
    terminateCodexAppServerProcess(process, timeoutMs)
  }
}

private fun terminateCodexAppServerProcess(process: Process, timeoutMs: Long) {
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
