// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.VcsException
import git4idea.GitWorkingTree
import git4idea.commands.GitLineHandlerListener
import git4idea.repo.GitRepository
import git4idea.util.StringScanner
import kotlinx.collections.immutable.toImmutableList

internal class GitListWorktreeLineListener(repository: GitRepository) : GitLineHandlerListener {
  private val currentRoot = repository.root.path
  private var badLineReported = 0
  private var currentWorktreePath: String? = null
  private var isFirst = true
  val trees: List<GitWorkingTree>
    get() = _trees.toImmutableList()
  private val _trees: MutableList<GitWorkingTree> = mutableListOf()

  fun report(line: String, e: VcsException? = null) {
    badLineReported++
    if (badLineReported < 5) {
      thisLogger().warn("Unexpected worktree output: $line", e)
    }
  }

  override fun onLineAvailable(line: @NlsSafe String, outputType: Key<*>) {
    if (line.isBlank()) return
    try {
      if (outputType == ProcessOutputType.STDOUT) {
        if (line == "detached") {
          reportTreeWithBranch(null)
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
            reportTreeWithBranch(value)
          }
          else -> report(line)
        }
      }
    }
    catch (e: VcsException) {
      report(line, e)
    }
  }

  private fun reportTreeWithBranch(branchFullName: String?) {
    val path = currentWorktreePath
    if (path != null) {
      _trees.add(GitWorkingTree(path, branchFullName, isFirst, path == currentRoot))
      isFirst = false
    }
    else {
      thisLogger().warn("'worktree' wasn't reported for branch ${branchFullName ?: "<detached>"}")
    }
    currentWorktreePath = null
  }
}