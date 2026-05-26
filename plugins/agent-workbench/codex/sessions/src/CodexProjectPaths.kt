// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.codex.sessions

import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

private object CodexProjectPathsLogCategory

private val LOG = logger<CodexProjectPathsLogCategory>()

internal fun parseProjectPath(path: String?): Path? {
  val trimmed = path?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
  return try {
    Path.of(trimmed)
  }
  catch (_: InvalidPathException) {
    null
  }
}

internal fun normalizeProjectPath(projectPath: Path?): Path? {
  val path = projectPath ?: return null
  val fileName = path.fileName?.toString() ?: return path
  val parentName = path.parent?.fileName?.toString()
  val normalized = when {
    fileName == ".idea" -> path.parent
    parentName == ".idea" -> path.parent?.parent
    fileName.endsWith(".ipr", ignoreCase = true) -> path.parent
    fileName.endsWith(".iws", ignoreCase = true) -> path.parent
    else -> path
  }
  return normalized ?: path
}

internal fun resolveProjectDirectoryFromPath(path: String): Path? {
  val parsed = parseProjectPath(path) ?: return null
  val normalized = normalizeProjectPath(parsed) ?: return null
  return normalized.takeIf { Files.isDirectory(it) }
}

@VisibleForTesting
internal fun resolveProjectDirectory(
  recentProjectPath: Path?,
  projectFilePath: String?,
  basePath: String?,
  guessedProjectDir: Path?,
): Path? {
  val candidates = sequenceOf(
    recentProjectPath,
    parseProjectPath(projectFilePath),
    parseProjectPath(basePath),
    guessedProjectDir,
  )
  for (candidate in candidates) {
    val normalized = normalizeProjectPath(candidate) ?: continue
    if (Files.isDirectory(normalized)) {
      return normalized
    }
  }
  return null
}

@OptIn(AwaitCancellationAndInvoke::class)
internal fun registerShutdownOnCancellation(scope: CoroutineScope, onShutdown: suspend CoroutineScope.() -> Unit) {
  val job = scope.coroutineContext[Job]
  if (job == null) {
    LOG.warn("Codex project session scope has no Job; shutdown hook not installed")
    return
  }
  scope.awaitCancellationAndInvoke(CoroutineName("Codex project session shutdown") + Dispatchers.IO) {
    LOG.debug { "Codex project session scope cancelling; shutting down client" }
    onShutdown()
  }
}
