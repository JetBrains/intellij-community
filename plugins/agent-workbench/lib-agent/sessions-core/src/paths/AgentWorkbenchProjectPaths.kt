// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ai.agent.sessions.core.paths

import com.intellij.platform.ai.agent.core.normalizeAgentWorkbenchPath
import com.intellij.platform.ai.agent.core.parseAgentWorkbenchPathOrNull
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString

private val BAZEL_PROJECT_FILE_SUFFIXES = listOf(
  ".bazelproject",
  ".blazeproject",
)

private val PROJECT_FILE_SUFFIXES = listOf(
  ".ipr",
  ".iws",
  *BAZEL_PROJECT_FILE_SUFFIXES.toTypedArray(),
)

@ApiStatus.Internal
fun resolveAgentWorkbenchProjectDirectory(identityPath: String?, projectBasePath: String? = null): String? {
  val normalizedBasePath = projectBasePath
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
  if (normalizedBasePath != null) {
    return normalizedBasePath
  }

  val parsedIdentityPath = identityPath
                             ?.takeIf { it.isNotBlank() }
                             ?.let(::normalizeAgentWorkbenchPath)
                             ?.let(::parseAgentWorkbenchPathOrNull)
                           ?: return null
  return resolveAgentWorkbenchProjectDirectory(parsedIdentityPath).invariantSeparatorsPathString
}

@ApiStatus.Internal
fun resolveAgentWorkbenchOwningProjectBasePath(identityPath: String?, projectBasePath: String?): String? {
  val normalizedBasePath = projectBasePath
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
    ?: return null
  val normalizedIdentityPath = identityPath
    ?.takeIf { it.isNotBlank() }
    ?.let(::normalizeAgentWorkbenchPath)
    ?: return null
  val basePath = parseAgentWorkbenchPathOrNull(normalizedBasePath)?.normalize() ?: return null
  val path = parseAgentWorkbenchPathOrNull(normalizedIdentityPath)?.normalize() ?: return null
  return normalizedBasePath.takeIf { path == basePath || path.startsWith(basePath) }
}

@ApiStatus.Internal
fun resolveAgentWorkbenchProjectDirectory(path: Path): Path {
  val normalizedPath = path.normalize()
  val fileName = normalizedPath.fileName?.toString().orEmpty()
  val parentName = normalizedPath.parent?.fileName?.toString()
  return when {
           fileName == ".idea" -> normalizedPath.parent
           parentName == ".idea" -> normalizedPath.parent?.parent
           BAZEL_PROJECT_FILE_SUFFIXES.any { suffix -> fileName.endsWith(suffix, ignoreCase = true) } ->
             normalizedPath.parent?.let(::resolveContainingGitWorktreeRoot) ?: normalizedPath.parent
           PROJECT_FILE_SUFFIXES.any { suffix -> fileName.endsWith(suffix, ignoreCase = true) } -> normalizedPath.parent
           else -> normalizedPath
         } ?: normalizedPath
}

private fun resolveContainingGitWorktreeRoot(startDirectory: Path): Path? {
  var directory: Path? = startDirectory.normalize()
  while (directory != null) {
    val gitPath = directory.resolve(".git")
    if (Files.isDirectory(gitPath) || Files.isRegularFile(gitPath)) {
      return directory
    }
    directory = directory.parent
  }
  return null
}
