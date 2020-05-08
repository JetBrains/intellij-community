// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.data.VcsLogData
import git4idea.i18n.GitBundle

internal fun findContainingBranches(data: VcsLogData, root: VirtualFile, hash: Hash): List<String> {
  val branchesGetter = data.containingBranchesGetter
  val branches = branchesGetter.getContainingBranchesQuickly(root, hash)
  if (branches == null) {
    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously<List<String>, RuntimeException>(
        {
          branchesGetter.getContainingBranchesSynchronously(root, hash)
        },
        GitBundle.getString("rebase.log.commit.editing.action.progress.containing.branches.title"),
        true,
        data.project
      )
  }
  return branches
}