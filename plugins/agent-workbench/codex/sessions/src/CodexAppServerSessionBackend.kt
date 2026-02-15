// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlin.io.path.invariantSeparatorsPathString

class CodexAppServerSessionBackend : CodexSessionBackend {
  override suspend fun listThreads(path: String, @Suppress("UNUSED_PARAMETER") openProject: Project?): List<CodexBackendThread> {
    val workingDirectory = resolveProjectDirectoryFromPath(path) ?: return emptyList()
    val codexService = service<SharedCodexAppServerService>()
    val threads = codexService.listThreads(workingDirectory)
    return threads.map { CodexBackendThread(it) }
  }

  override suspend fun prefetchThreads(paths: List<String>): Map<String, List<CodexBackendThread>> {
    if (paths.isEmpty()) return emptyMap()
    val codexService = service<SharedCodexAppServerService>()
    val allThreads = codexService.listAllThreads()
    val pathToCwd = paths.mapNotNull { path ->
      resolveProjectDirectoryFromPath(path)?.let { dir ->
        path to normalizeRootPath(dir.invariantSeparatorsPathString)
      }
    }
    return pathToCwd.associate { (path, cwdFilter) ->
      val matching = allThreads.filter { it.cwd == cwdFilter }
      path to matching.map { CodexBackendThread(it) }
    }
  }
}

