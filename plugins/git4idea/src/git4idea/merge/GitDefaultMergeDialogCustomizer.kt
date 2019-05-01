// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.xml.util.XmlStringUtil
import git4idea.GitBranch
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.GitUtil.CHERRY_PICK_HEAD
import git4idea.GitUtil.MERGE_HEAD
import git4idea.GitVcs
import git4idea.history.GitLogUtil
import git4idea.rebase.GitRebaseUtils
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.io.File
import java.io.IOException

open class GitDefaultMergeDialogCustomizer(
  private val project: Project
) : MergeDialogCustomizer() {
  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>): String {
    val repos = GitUtil.getRepositoriesForFiles(project, files)

    val mergeBranches = repos.map { resolveMergeBranch(it)?.presentable }
    if (mergeBranches.any { it != null }) {
      return buildString {
        append("<html>Merging ")
        append(mergeBranches.toSet().singleOrNull()?.let { "branch <b>${XmlStringUtil.escapeString(it)}</b>" } ?: "diverging branches ")
        append(" into ")
        append(getSingleCurrentBranchName(repos)?.let { "branch <b>${XmlStringUtil.escapeString(it)}</b>" }
               ?: "diverging branches")
      }
    }

    val rebaseOntoBranches = repos.map { resolveRebaseOntoBranch(it) }
    if (rebaseOntoBranches.any { it != null }) {
      val singleCurrentBranch = getSingleCurrentBranchName(repos)
      val singleOntoBranch = rebaseOntoBranches.toSet().singleOrNull()
      return getDescriptionForRebase(singleCurrentBranch, singleOntoBranch?.branchName, singleOntoBranch?.hash)
    }

    val cherryPickCommitDetails = repos.map { loadCherryPickCommitDetails(it) }
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
          append("<code>\"${XmlStringUtil.escapeString(singleCherryPick.commitMessage)}\"</code>")
        }
      }
    }

    return super.getMultipleFileMergeDescription(files)
  }

  override fun getLeftPanelTitle(file: VirtualFile): String {
    val currentBranch = GitRepositoryManager.getInstance(project).getRepositoryForFile(file)?.currentBranchName
    return if (currentBranch != null) getDefaultLeftPanelTitleForBranch(currentBranch)
           else super.getLeftPanelTitle(file)
  }

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String {
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForFile(file)
                     ?: return super.getRightPanelTitle(file, revisionNumber)

    val branchBeingMerged = resolveMergeBranch(repository) ?: resolveRebaseOntoBranch(repository)
    if (branchBeingMerged != null) {
      return getDefaultRightPanelTitleForBranch(branchBeingMerged.branchName, branchBeingMerged.hash)
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

  private fun loadCherryPickCommitDetails(repository: GitRepository): CherryPickDetails? {
    val cherryPickHead = tryResolveRef(repository, CHERRY_PICK_HEAD) ?: return null

    val shortDetails = GitLogUtil.collectMetadata(project, GitVcs.getInstance(project), repository.root,
                                                  listOf(cherryPickHead.asString()))

    val result = shortDetails.singleOrNull() ?: return null
    return CherryPickDetails(cherryPickHead.toShortString(), result.author.name, result.subject)
  }

  private data class CherryPickDetails(val shortHash: String, val authorName: String, val commitMessage: String)
}

fun getDescriptionForRebase(rebasingBranch: String?, baseBranch: String?, baseHash: Hash?): String {
  return buildString {
    append("<html>Rebasing ")
    if (rebasingBranch != null) append("branch <b>${XmlStringUtil.escapeString(rebasingBranch)}</b> ")
    append("onto ")
    appendBranchName(baseBranch, baseHash)
  }
}

fun getDefaultLeftPanelTitleForBranch(branchName: String): String {
  return "<html>${XmlStringUtil.escapeString(DiffBundle.message("merge.version.title.our"))}, branch <b>" +
         "${XmlStringUtil.escapeString(branchName)}</b>"
}

fun getDefaultRightPanelTitleForBranch(branchName: String?, baseHash: Hash?): String {
  return buildString {
    append("<html>Changes from ")
    appendBranchName(branchName, baseHash)
  }
}

private fun StringBuilder.appendBranchName(branchName: String?, hash: Hash?) {
  if (branchName != null) {
    append("branch <b>${XmlStringUtil.escapeString(branchName)}</b>")
    if (hash != null) append(", revision ${hash.toShortString()}")
  }
  else if (hash != null) {
    append("<b>${hash.toShortString()}</b>")
  }
  else {
    append("diverging branches")
  }
}

private fun resolveMergeBranchOrCherryPick(repository: GitRepository): String? {
  val mergeBranch = resolveMergeBranch(repository)
  if (mergeBranch != null) return mergeBranch.presentable

  val rebaseOntoBranch = resolveRebaseOntoBranch(repository)
  if (rebaseOntoBranch != null) return rebaseOntoBranch.presentable

  val cherryHead = tryResolveRef(repository, CHERRY_PICK_HEAD)
  if (cherryHead != null) return "cherry-pick"
  return null
}

private fun resolveMergeBranch(repository: GitRepository): RefInfo? {
  val mergeHead = tryResolveRef(repository, MERGE_HEAD) ?: return null
  return resolveBranchName(repository, mergeHead)
}

private fun resolveRebaseOntoBranch(repository: GitRepository): RefInfo? {
  val rebaseDir = GitRebaseUtils.getRebaseDir(repository.project, repository.root) ?: return null
  val ontoHash = try {
    FileUtil.loadFile(File(rebaseDir, "onto")).trim()
  }
  catch (e: IOException) {
    return null
  }

  val repo = GitRepositoryManager.getInstance(repository.project).getRepositoryForRoot(repository.root) ?: return null
  return resolveBranchName(repo, HashImpl.build(ontoHash))
}

private fun resolveBranchName(repository: GitRepository, hash: Hash): RefInfo {
  var branches: Collection<GitBranch> = repository.branches.findLocalBranchesByHash(hash)
  if (branches.isEmpty()) branches = repository.branches.findRemoteBranchesByHash(hash)
  return RefInfo(hash, branches.singleOrNull()?.name)
}

private fun tryResolveRef(repository: GitRepository, ref: String): Hash? {
  try {
    val revision = GitRevisionNumber.resolve(repository.project, repository.root, ref)
    return HashImpl.build(revision.asString())
  }
  catch (e: VcsException) {
    return null
  }
}

fun getSingleMergeBranchName(roots: Collection<GitRepository>): String? {
  return roots.asSequence()
    .mapNotNull { repo -> resolveMergeBranchOrCherryPick(repo) }
    .distinct()
    .singleOrNull()
}

fun getSingleCurrentBranchName(roots: Collection<GitRepository>): String? {
  return roots.asSequence()
    .mapNotNull { repo -> repo.currentBranchName }
    .distinct()
    .singleOrNull()
}

private data class RefInfo(val hash: Hash, val branchName: String?) {
  val presentable: String = branchName ?: hash.toShortString()
}