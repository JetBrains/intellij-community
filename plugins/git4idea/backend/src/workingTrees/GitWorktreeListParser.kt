// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsException
import git4idea.GitWorkingTree
import git4idea.config.GitExecutable
import git4idea.util.StringScanner
import kotlinx.collections.immutable.toImmutableList
import java.nio.file.Path

internal class GitWorktreeListParser(
  private val executable: GitExecutable,
  private val currentRoot: Path,
  private val currentGitDir: Path? = null,
) {
  private var badLineReported = 0
  private var currentWorktreePath: String? = null
  private var branchFullName: String? = null
  private var isDetached = false
  private var isLocked = false
  private var isPrunable = false
  private var currentHeadHash: String? = null
  private var isFirst = true
  private val _trees: MutableList<GitWorkingTree> = mutableListOf()

  fun parseOrEmpty(lines: List<String>): List<GitWorkingTree> {
    lines.forEach(::handleLine)
    return _trees.toImmutableList()
  }

  private fun handleLine(line: String) {
    if (line.isBlank()) {
      createWorkingTree(currentWorktreePath, branchFullName, isDetached, isFirst, isLocked, isPrunable, currentHeadHash)
      return
    }

    try {
        if (line == "detached") {
          isDetached = true
          return
        }
        if (line == "locked") {
          isLocked = true
          return
        }
        val scanner = StringScanner(line)
        val name = scanner.spaceToken() ?: return
        val value = scanner.line() ?: return
        when (name) {
          "worktree" -> {
            currentWorktreePath = value
          }
          "HEAD" -> {
            currentHeadHash = value
          }
          "branch" -> {
            branchFullName = value
          }
          "prunable" -> {
            isPrunable = true
          }
          "locked" -> {
            isLocked = true
          }
          else -> report(line)
        }
    }
    catch (e: VcsException) {
      report(line, e)
    }
  }

  private fun createWorkingTree(
    path: String?,
    branch: String?,
    detached: Boolean,
    main: Boolean,
    locked: Boolean,
    prunable: Boolean,
    headHash: String?,
  ) {
    if (path == null) {
      LOG.warn("'worktree' wasn't reported for branch ${branch ?: "<detached>"}")
    }
    else if (!detached && branch == null) {
      LOG.warn("'branch' wasn't reported for path $path")
    }
    else {
      val convertedPath = executable.convertFilePathBack(path, currentRoot)
      val isCurrent = convertedPath == currentRoot || (currentGitDir != null && convertedPath == currentGitDir)
      // For a submodule, git reports the git-dir as the worktree path; expose the real working-tree root instead.
      val effectivePath = if (isCurrent) currentRoot else convertedPath
      val hash = if (detached) headHash else null
      _trees.add(GitWorkingTree(effectivePath.toString(), branch, main, isCurrent, locked, prunable, hash))
    }
    currentWorktreePath = null
    branchFullName = null
    isDetached = false
    isLocked = false
    isPrunable = false
    currentHeadHash = null
    isFirst = false
  }

  private fun report(line: String, e: VcsException? = null) {
    badLineReported++
    if (badLineReported < 5) {
      LOG.warn("Unexpected worktree output: $line", e)
    }
  }

  companion object {
    private val LOG = thisLogger()
  }
}