// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.vcs.git.ref.GitRefUtil
import org.jetbrains.annotations.NonNls

/**
 * Represents a Git branch, local or remote.
 *
 * Local and remote branches are different in that nature, that remote branch has complex name ("origin/master") containing both
 * the name of the remote and the name of the branch. And while the standard name of the remote branch if origin/master,
 * in certain cases we must operate with the name which the branch has at the remote: "master".
 *
 * It contains information about the branch name and the hash it points to.
 * Note that the object (including the hash) is immutable. That means that if branch reference move along, you have to get new instance
 * of the GitBranch object, probably from [git4idea.repo.GitRepository.getBranches] or [git4idea.repo.GitRepository.getCurrentBranch].
 *
 * GitBranches are equal, if their full names are equal. That means that if two GitBranch objects have different hashes, they
 * are considered equal. But in this case an error if logged, because it means that one of this GitBranch instances is out-of-date, and
 * it is required to use an [git4idea.repo.GitRepository.update] updated version.
 */
abstract class GitBranch protected constructor(name: String) : GitReference(GitRefUtil.stripRefsPrefix(name)) {
  /**
   * Set to true if the branch is remote
   */
  abstract val isRemote: Boolean

  companion object {
    const val REFS_HEADS_PREFIX: @NonNls String = "refs/heads/" // Prefix for local branches ({@value})
    const val REFS_REMOTES_PREFIX: @NonNls String = "refs/remotes/" // Prefix for remote branches ({@value})
  }
}
