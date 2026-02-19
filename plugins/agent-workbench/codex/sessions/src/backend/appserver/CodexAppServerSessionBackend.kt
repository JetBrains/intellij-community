// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions.backend.appserver

import com.intellij.agent.workbench.codex.common.normalizeRootPath
import com.intellij.agent.workbench.codex.sessions.backend.CodexBackendThread
import com.intellij.agent.workbench.codex.sessions.backend.CodexSessionBackend
import com.intellij.agent.workbench.codex.sessions.resolveProjectDirectoryFromPath
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

    val pathFilters = resolvePathFilters(paths)
    if (pathFilters.isEmpty()) return emptyMap()

    val targetCwds = pathFilters.mapTo(HashSet(pathFilters.size)) { (_, cwdFilter) -> cwdFilter }
    val threadsByCwd = HashMap<String, MutableList<CodexBackendThread>>(targetCwds.size)
    val codexService = service<SharedCodexAppServerService>()
    for (thread in codexService.listAllThreads()) {
      val cwd = thread.cwd ?: continue
      if (!targetCwds.contains(cwd)) continue
      threadsByCwd.getOrPut(cwd) { ArrayList() }.add(CodexBackendThread(thread))
    }

    return pathFilters.associate { (path, cwdFilter) ->
      path to threadsByCwd[cwdFilter].orEmpty()
    }
  }
}

private fun resolvePathFilters(paths: List<String>): List<Pair<String, String>> {
  return paths.mapNotNull { path ->
    resolveProjectDirectoryFromPath(path)?.let { directory ->
      path to normalizeRootPath(directory.invariantSeparatorsPathString)
    }
  }
}
