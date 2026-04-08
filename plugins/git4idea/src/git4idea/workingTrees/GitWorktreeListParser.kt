// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.vcs.VcsException
import git4idea.GitWorkingTree
import git4idea.config.GitExecutable
import git4idea.util.StringScanner
import kotlinx.collections.immutable.toImmutableList
import java.nio.file.Path

internal class GitWorktreeListParser(private val executable: GitExecutable, private val currentRoot: Path) {
  private var badLineReported = 0
  private var currentWorktreePath: String? = null
  private var branchFullName: String? = null
  private var isDetached = false
  private var isLocked = false
  private var isPrunable = false
  private var isFirst = true
  private val _trees: MutableList<GitWorkingTree> = mutableListOf()

  fun parseOrEmpty(lines: List<String>): List<GitWorkingTree> {
    lines.forEach(::handleLine)
    return _trees.toImmutableList()
  }

  private fun handleLine(line: String) {
    if (line.isBlank()) {
      createWorkingTree(currentWorktreePath, branchFullName, isDetached, isFirst, isLocked, isPrunable)
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
          "HEAD" -> { //ignore for now
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
  ) {
    if (path == null) {
      LOG.warn("'worktree' wasn't reported for branch ${branch ?: "<detached>"}")
    }
    else if (!detached && branch == null) {
      LOG.warn("'branch' wasn't reported for path $path")
    }
    else {
      val convertedPath = executable.convertFilePathBack(path, currentRoot)
      _trees.add(GitWorkingTree(convertedPath.toString(), branch, main, convertedPath == currentRoot, locked, prunable))
    }
    currentWorktreePath = null
    branchFullName = null
    isDetached = false
    isLocked = false
    isPrunable = false
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