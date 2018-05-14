// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil

/**
 * @author yole
 */
class GitDefaultMergeDialogCustomizer(
  private val gitMergeProvider: GitMergeProvider
) : MergeDialogCustomizer() {
  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): String? {
    val filesByRoot = GitUtil.sortFilesByGitRoot(files)
    val mergeBranches = filesByRoot.keys.map { gitMergeProvider.resolveMergeBranch(it) }
    if (mergeBranches.any { it != null }) {
      return buildString {
        append("Merging ")
        append(mergeBranches.toSet().singleOrNull()?.let { "branch $it" } ?: "diverging branches ")
        append(" into ")
        append(gitMergeProvider.getSingleCurrentBranchName(filesByRoot.keys)?.let { "branch $it" } ?: "diverging branches")
      }
    }

    return super.getMultipleFileMergeDescription(files)
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String? {
    val branchBeingMerged = gitMergeProvider.resolveMergeBranch(file) ?: return super.getRightPanelTitle(file, revisionNumber)
    return "Changes from branch $branchBeingMerged"
  }
}
