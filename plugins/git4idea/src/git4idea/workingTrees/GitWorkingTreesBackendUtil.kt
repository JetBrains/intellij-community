// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitReference
import git4idea.GitWorkingTree
import git4idea.repo.GitRepository

internal object GitWorkingTreesBackendUtil {
  internal const val TOOLWINDOW_TAB_ID: String = "Working Trees"

  /**
   * See [com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil.getWorkingTreeWithRef]
   */
  fun getWorkingTreeWithRef(reference: GitReference, repository: GitRepository, skipCurrentWorkingTree: Boolean): GitWorkingTree? {
    return GitWorkingTreesUtil.getWorkingTreeWithRef(reference, repository, skipCurrentWorkingTree) {
      repository.workingTreeHolder.getWorkingTrees()
    }
  }
}