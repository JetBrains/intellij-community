// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe

/**
 * Detects the error which happens when `git checkout`/`git switch`/`git worktree add` refuses to
 * check out a branch that is already checked out in another worktree of the same repository, e.g.:
 * ```
 * fatal: 'my-branch' is already used by worktree at '/path/to/other/worktree'
 * ```
 */
internal class GitBranchAlreadyCheckedOutInOtherWorktreeDetector : GitLineEventDetector {

  @Volatile
  var match: Match? = null
    private set

  override val isDetected: Boolean
    get() = match != null

  override fun onLineAvailable(line: @NlsSafe String?, outputType: Key<*>?) {
    if (line == null) return

    val matchResult = PATTERN.matchEntire(line) ?: return
    match = Match(matchResult.groupValues[1], matchResult.groupValues[2])
  }

  internal data class Match(val branchName: String, val worktreePath: String?)

  companion object {
    private val PATTERN = ".*fatal: '(.*)' is already used by worktree at '(.*)'.*".toRegex()

    @JvmStatic
    fun matchInOutput(lines: List<String>): Match? {
      val detector = GitBranchAlreadyCheckedOutInOtherWorktreeDetector()
      for (line in lines) {
        detector.onLineAvailable(line, null)
        if (detector.isDetected) {
          return detector.match
        }
      }
      return null
    }
  }
}
