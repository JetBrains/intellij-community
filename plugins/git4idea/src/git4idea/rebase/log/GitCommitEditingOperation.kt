// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseEditorHandler
import git4idea.rebase.GitRebaseProcess
import git4idea.rebase.GitRebaseSpec
import git4idea.rebase.interactive.getRebaseUpstreamFor
import git4idea.repo.GitRepository

internal abstract class GitCommitEditingOperation(protected val repository: GitRepository) {
  protected val project = repository.project

  protected fun rebase(
    commits: List<VcsCommitMetadata>,
    rebaseEditor: GitRebaseEditorHandler,
    preserveMerges: Boolean = false,
    initialHead: String? = null
  ): GitCommitEditingOperationResult {
    val lastCommit = commits.last()
    val base = getRebaseUpstreamFor(lastCommit)
    val params = GitRebaseParams.editCommits(
      repository.vcs.version,
      base,
      rebaseEditor,
      preserveMerges,
      GitRebaseParams.AutoSquashOption.DISABLE
    )
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(project, params, listOf(repository), indicator)
    val process = GitMultipleCommitEditingProcess(repository, params, spec, initialHead)
    process.rebase()
    return process.result
  }

  private class GitMultipleCommitEditingProcess(
    private val repository: GitRepository,
    private val params: GitRebaseParams,
    spec: GitRebaseSpec,
    initialHead: String?,
  ) : GitRebaseProcess(repository.project, spec, null) {
    init {
      repository.update()
    }

    private val initialHead = initialHead ?: repository.currentRevision!!
    var result: GitCommitEditingOperationResult = GitCommitEditingOperationResult.Incomplete

    @RequiresBackgroundThread
    override fun notifySuccess() {
      repository.update()
      val newHead = repository.currentRevision!!
      result = GitCommitEditingOperationResult.Complete(repository, params.upstream, initialHead, newHead)
    }
  }
}