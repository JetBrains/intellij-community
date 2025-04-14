// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea

import com.intellij.openapi.util.NlsSafe
import git4idea.branch.GitBranchUtil
import git4idea.repo.GitRemote

class GitStandardRemoteBranch(remote: GitRemote, nameAtRemote: String) :
  GitRemoteBranch("${remote.name}/${GitBranchUtil.stripRefsPrefix(nameAtRemote)}", remote) {
  override val fullName: @NlsSafe String
    get() = REFS_REMOTES_PREFIX + name

  override val nameForRemoteOperations: String = GitBranchUtil.stripRefsPrefix(nameAtRemote)

  override val nameForLocalOperations: String = name

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false
    if (!super.equals(o)) return false

    val branch = o as GitStandardRemoteBranch

    if (this.nameForRemoteOperations != branch.nameForRemoteOperations) return false
    if (remote != branch.remote) return false

    return true
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + remote.hashCode()
    result = 31 * result + nameForRemoteOperations.hashCode()
    return result
  }

  override fun compareTo(o: GitReference?): Int {
    if (o is GitStandardRemoteBranch) {
      // optimization: do not build getFullName
      return REFS_NAMES_COMPARATOR.compare(name, o.name)
    }
    return super.compareTo(o)
  }
}
