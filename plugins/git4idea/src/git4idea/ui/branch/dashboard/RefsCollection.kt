// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import git4idea.branch.GitBranchType
import git4idea.branch.GitRefType
import git4idea.branch.GitTagType

internal data class RefsCollection(val localBranches: MutableCollection<BranchInfo>, val remoteBranches: MutableCollection<BranchInfo>, val tags: MutableCollection<RefInfo>) {
  fun forEach(action: (MutableCollection<out RefInfo>, GitRefType) -> Unit) {
    for ((refs, group) in listOf(localBranches to GitBranchType.LOCAL, remoteBranches to GitBranchType.REMOTE, tags to GitTagType)) {
      action(refs, group)
    }
  }
}
