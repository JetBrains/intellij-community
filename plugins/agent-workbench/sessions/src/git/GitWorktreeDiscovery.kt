// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.git

import com.intellij.agent.workbench.common.normalizeAgentWorkbenchPathOrNull
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

private val LOG = logger<GitWorktreeDiscovery>()
private const val GIT_COMMAND = "git"
private const val PROCESS_TIMEOUT_MS = 10_000L

internal data class GitWorktreeInfo(
  @JvmField val path: String,
  @JvmField val branch: String?,
  @JvmField val isMain: Boolean,
)

internal object GitWorktreeDiscovery {
  /**
   * Returns the main repo root path, or `null` if the path is not inside a git repository.
   *
   * When [projectPath] is the main checkout the method returns [projectPath] itself.
   * When [projectPath] is a linked worktree it follows the `.git` file back to the main checkout.
   */
  // TODO: provide better approximation
  fun detectRepoRoot(projectPath: String): String? {
    return try {
      val dir = Path.of(projectPath)
      val dotGit = dir.resolve(".git")
      when {
        dotGit.isDirectory() -> normalizeAgentWorkbenchPathOrNull(projectPath)
        dotGit.isRegularFile() -> resolveRepoRootFromDotGitFile(dotGit, dir)
        else -> null
      }
    }
    catch (e: Throwable) {
      LOG.debug("Failed to detect repo root for $projectPath", e)
      null
    }
  }

  /**
   * Discovers all worktrees (main + linked) for the repository at [projectPath].
   *
   * The method runs `git worktree list --porcelain` and returns parsed entries as-is.
   * It can be called from any path within the repository (main or linked worktree).
   * Returns an empty list on any failure (git not installed, not a repo, timeout, etc.).
   */
  suspend fun discoverWorktrees(projectPath: String): List<GitWorktreeInfo> {
    return withContext(Dispatchers.IO) {
      try {
        val directory = Path.of(projectPath)
        if (!Files.isDirectory(directory)) return@withContext emptyList()

        val gitExecutable = findGitExecutable() ?: return@withContext emptyList()
        val output = runGitWorktreeList(gitExecutable, directory) ?: return@withContext emptyList()
        parseWorktreeListPorcelain(output)
      }
      catch (e: Throwable) {
        LOG.debug("Failed to discover worktrees for $projectPath", e)
        emptyList()
      }
    }
  }

  // ---- internal / visible-for-testing ----

  internal fun parseGitFile(content: String): String? {
    val line = content.lineSequence().firstOrNull()?.trim() ?: return null
    if (!line.startsWith("gitdir:")) return null
    return line.removePrefix("gitdir:").trim().takeIf { it.isNotEmpty() }
  }

  internal fun resolveRepoRootFromGitDir(gitDir: String): String? {
    val normalized = normalizeAgentWorkbenchPathOrNull(gitDir) ?: return null
    val worktreesIdx = normalized.lastIndexOf("/.git/worktrees/")
    if (worktreesIdx < 0) return null
    return normalized.substring(0, worktreesIdx)
  }

  internal fun parseWorktreeListPorcelain(output: String): List<GitWorktreeInfo> {
    val result = mutableListOf<GitWorktreeInfo>()
    var currentPath: String? = null
    var currentBranch: String? = null
    var isFirst = true

    for (line in output.lineSequence()) {
      if (line.isBlank()) {
        val path = currentPath
        if (path != null) {
          result.add(GitWorktreeInfo(
            path = normalizeAgentWorkbenchPathOrNull(path) ?: path,
            branch = currentBranch,
            isMain = isFirst,
          ))
          isFirst = false
        }
        currentPath = null
        currentBranch = null
        continue
      }
      when {
        line.startsWith("worktree ") -> currentPath = line.removePrefix("worktree ")
        line.startsWith("branch ") -> currentBranch = line.removePrefix("branch ")
        line == "detached" -> currentBranch = null
      }
    }

    val path = currentPath
    if (path != null) {
      result.add(GitWorktreeInfo(
        path = normalizeAgentWorkbenchPathOrNull(path) ?: path,
        branch = currentBranch,
        isMain = isFirst,
      ))
    }

    return result
  }

}

private fun resolveRepoRootFromDotGitFile(dotGitFile: Path, worktreeDir: Path): String? {
  val content = dotGitFile.readText()
  val rawGitDir = GitWorktreeDiscovery.parseGitFile(content) ?: return null
  val gitDirPath = try {
    val parsed = Path.of(rawGitDir)
    if (parsed.isAbsolute) parsed else worktreeDir.resolve(parsed).normalize()
  }
  catch (_: InvalidPathException) {
    return null
  }
  return GitWorktreeDiscovery.resolveRepoRootFromGitDir(gitDirPath.invariantSeparatorsPathString)
}

private fun findGitExecutable(): String? {
  return PathEnvironmentVariableUtil.findExecutableInPathOnAnyOS(GIT_COMMAND)?.absolutePath
}

private fun runGitWorktreeList(gitExecutable: String, directory: Path): String? {
  val commandLine = GeneralCommandLine(gitExecutable, "worktree", "list", "--porcelain")
    .withWorkingDirectory(directory)
  val handler = CapturingProcessHandler(commandLine)
  val result = handler.runProcess(PROCESS_TIMEOUT_MS.toInt())
  if (result.isTimeout || result.exitCode != 0) return null
  return result.stdout
}

internal fun shortBranchName(fullRef: String?): String? {
  if (fullRef == null) return null
  return fullRef.removePrefix("refs/heads/")
}

internal fun worktreeDisplayName(path: String): String {
  return try {
    Path.of(path).name
  }
  catch (_: InvalidPathException) {
    path
  }
}
