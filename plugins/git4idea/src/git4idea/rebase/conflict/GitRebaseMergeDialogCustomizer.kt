// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.conflict

import com.intellij.diff.DiffEditorTitleCustomizer
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.history.GitHistoryUtils
import git4idea.i18n.GitBundleExtensions.html
import git4idea.merge.*
import git4idea.rebase.GitRebaseSpec
import git4idea.repo.GitRepository

private val LOG = logger<GitRebaseMergeDialogCustomizer>()

internal fun createRebaseDialogCustomizer(repository: GitRepository, rebaseSpec: GitRebaseSpec): MergeDialogCustomizer {
  val rebaseParams = rebaseSpec.params
  if (rebaseParams == null) {
    return GitDefaultMergeDialogCustomizer(repository.project)
  }
  val currentBranchAtTheStartOfRebase = rebaseSpec.initialBranchNames[repository]

  // check that upstream is HEAD to overcome a hack: passing HEAD into `git rebase HEAD branch`
  // to avoid passing branch names for different repositories
  val upstream = rebaseParams.upstream.takeIf { it != GitUtil.HEAD } ?: currentBranchAtTheStartOfRebase
  val branch = rebaseParams.branch ?: currentBranchAtTheStartOfRebase

  if (upstream == null || branch == null) {
    return GitDefaultMergeDialogCustomizer(repository.project)
  }

  val rebaseHead = try {
    HashImpl.build(GitRevisionNumber.resolve(repository.project, repository.root, GitUtil.REBASE_HEAD).asString())
  }
  catch (e: VcsException) {
    LOG.warn(e)
    null
  }

  val mergeBase = try {
    GitHistoryUtils.getMergeBase(repository.project, repository.root, upstream, branch)?.let {
      HashImpl.build(it.rev)
    }
  }
  catch (e: VcsException) {
    LOG.warn(e)
    null
  }

  return GitRebaseMergeDialogCustomizer(repository, upstream, branch, rebaseHead, mergeBase)
}

private class GitRebaseMergeDialogCustomizer(
  private val repository: GitRepository,
  upstream: String,
  @NlsSafe private val rebasingBranch: String,
  private val ingoingCommit: Hash?,
  private val mergeBase: Hash?
) : MergeDialogCustomizer() {
  private val baseHash: Hash?

  @NlsSafe
  private val basePresentable: String?

  @NlsSafe
  private val baseBranch: String?

  init {
    if (upstream.matches("[a-fA-F0-9]{40}".toRegex())) {
      basePresentable = VcsLogUtil.getShortHash(upstream)
      baseBranch = null
      baseHash = HashImpl.build(upstream)
    }
    else {
      basePresentable = upstream
      baseBranch = upstream
      baseHash = null
    }
  }

  override fun getMultipleFileMergeDescription(files: MutableCollection<VirtualFile>) = getDescriptionForRebase(rebasingBranch, baseBranch, baseHash)

  override fun getLeftPanelTitle(file: VirtualFile) = getDefaultLeftPanelTitleForBranch(rebasingBranch)

  override fun getRightPanelTitle(file: VirtualFile, revisionNumber: VcsRevisionNumber?): String {
    val hash = if (revisionNumber != null) HashImpl.build(revisionNumber.asString()) else baseHash
    return getDefaultRightPanelTitleForBranch(baseBranch, hash)
  }

  override fun getColumnNames() = listOf(
    GitMergeProvider.calcColumnName(false, rebasingBranch),
    GitMergeProvider.calcColumnName(true, basePresentable)
  )

  override fun getTitleCustomizerList(file: FilePath) = DiffEditorTitleCustomizerList(
    getLeftTitleCustomizer(file),
    null,
    getRightTitleCustomizer(file)
  )

  private fun getLeftTitleCustomizer(file: FilePath): DiffEditorTitleCustomizer? {
    if (ingoingCommit == null) {
      return null
    }
    return getTitleWithCommitDetailsCustomizer(
      html(
        "rebase.conflict.diff.dialog.left.title",
        ingoingCommit.toShortString(),
        HtmlChunk.text(rebasingBranch).bold()
      ),
      repository,
      file,
      ingoingCommit.asString()
    )
  }

  private fun getRightTitleCustomizer(file: FilePath): DiffEditorTitleCustomizer? {
    if (mergeBase == null) {
      return null
    }
    val title = if (baseBranch != null) {
      html("rebase.conflict.diff.dialog.right.with.branch.title", HtmlChunk.text(baseBranch).bold())
    }
    else {
      html("rebase.conflict.diff.dialog.right.simple.title")
    }
    return getTitleWithCommitsRangeDetailsCustomizer(title, repository, file, Pair(mergeBase.asString(), GitUtil.HEAD))
  }
}

