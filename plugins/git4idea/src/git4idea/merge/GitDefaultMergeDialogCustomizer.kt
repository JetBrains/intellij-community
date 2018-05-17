// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.GitUtil.CHERRY_PICK_HEAD
import git4idea.GitVcs
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepositoryManager

/**
 * @author yole
 */
class GitDefaultMergeDialogCustomizer(
  private val gitMergeProvider: GitMergeProvider
) : MergeDialogCustomizer() {
  private val project = gitMergeProvider.project

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

    val cherryPickCommitDetails = filesByRoot.keys.map { loadCherryPickCommitDetails(it) }
    if (cherryPickCommitDetails.any { it != null }) {
      val notNullCherryPickCommitDetails = cherryPickCommitDetails.filterNotNull()
      val singleCherryPick = notNullCherryPickCommitDetails.distinctBy { it.authorName + it.commitMessage }.singleOrNull()
      return buildString {
        append("<html>Conflicts during cherry-picking ")
        if (notNullCherryPickCommitDetails.size == 1) {
          append("commit <code>${notNullCherryPickCommitDetails.single().shortHash}</code> ")
        }
        else {
          append("multiple commits ")
        }
        if (singleCherryPick != null) {
          append("made by ${XmlStringUtil.escapeString(singleCherryPick.authorName)}<br/>")
          append("<code>${XmlStringUtil.escapeString(singleCherryPick.commitMessage)}</code>")
        }
      }
    }

    return super.getMultipleFileMergeDescription(files)
  }

  override fun getLeftPanelTitle(file: VirtualFile): String? {
    val repo = GitRepositoryManager.getInstance(project).getRepositoryForFile(file)
    if (repo != null) {
      return "<html>" + XmlStringUtil.escapeString(super.getLeftPanelTitle(file)) +
             ", branch <b>${XmlStringUtil.escapeString(repo.currentBranchName)}</b>"
    }
    return super.getLeftPanelTitle(file)
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String? {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file)
                     ?: return super.getRightPanelTitle(file, revisionNumber)

    val branchBeingMerged = gitMergeProvider.resolveMergeBranch(repository)
    if (branchBeingMerged != null) {
      val branch = "<html>Changes from branch <b>${XmlStringUtil.escapeString(branchBeingMerged)}</b>"
      if (revisionNumber is GitRevisionNumber) {
        return branch + ", revision ${revisionNumber.shortRev}"
      }
    }

    val cherryPickHead = try {
      GitRevisionNumber.resolve(project, repository.root, CHERRY_PICK_HEAD)
    }
    catch (e: VcsException) {
      null
    }

    if (cherryPickHead != null) {
      return "<html>Changes from cherry-pick <code>${cherryPickHead.shortRev}</code>"
    }

    if (revisionNumber is GitRevisionNumber) {
      return DiffBundle.message("merge.version.title.their.with.revision", revisionNumber.shortRev)
    }
    return super.getRightPanelTitle(file, revisionNumber)
  }

  private fun loadCherryPickCommitDetails(root: VirtualFile): CherryPickDetails? {
    val cherryPickHead = try {
      GitRevisionNumber.resolve(project, root, CHERRY_PICK_HEAD)
    }
    catch (e: VcsException) {
      return null
    }

    val shortDetails = GitLogUtil.collectShortDetails(project, GitVcs.getInstance(project), root,
                                                      listOf(cherryPickHead.rev))

    val result = shortDetails.singleOrNull() ?: return null
    return CherryPickDetails(cherryPickHead.shortRev, result.author.name, result.subject)
  }

  private data class CherryPickDetails(val shortHash: String, val authorName: String, val commitMessage: String)
}
