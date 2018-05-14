// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.repo.GitRepositoryManager

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
        append("<html>Merging ")
        append(mergeBranches.toSet().singleOrNull()?.let { "branch <b>${XmlStringUtil.escapeString(it)}</b>" } ?: "diverging branches ")
        append(" into ")
        append(gitMergeProvider.getSingleCurrentBranchName(filesByRoot.keys)?.let { "branch <b>${XmlStringUtil.escapeString(it)}</b>" } ?: "diverging branches")
      }
    }

    return super.getMultipleFileMergeDescription(files)
  }

  override fun getLeftPanelTitle(file: VirtualFile): String? {
    val repo = GitRepositoryManager.getInstance(gitMergeProvider.project).getRepositoryForFile(file)
    if (repo != null) {
      return "<html>" + XmlStringUtil.escapeString(super.getLeftPanelTitle(file)) +
             ", branch <b>${XmlStringUtil.escapeString(repo.currentBranchName)}</b>"
    }
    return super.getLeftPanelTitle(file)
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String? {
    val branchBeingMerged = gitMergeProvider.resolveMergeBranch(file)
    if (branchBeingMerged != null) {
      val branch = "<html>Changes from branch <b>${XmlStringUtil.escapeString(branchBeingMerged)}</b>"
      if (revisionNumber is GitRevisionNumber) {
        return branch + ", revision ${revisionNumber.shortRev}"
      }
    }
    if (revisionNumber is GitRevisionNumber) {
      return DiffBundle.message("merge.version.title.their.with.revision", revisionNumber.shortRev)
    }
    return super.getRightPanelTitle(file, revisionNumber)
  }
}
