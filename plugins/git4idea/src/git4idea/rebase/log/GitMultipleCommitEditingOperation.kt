// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.branch.GitRebaseParams
import git4idea.rebase.GitRebaseEditorHandler
import git4idea.rebase.GitRebaseProcess
import git4idea.rebase.GitRebaseSpec
import git4idea.repo.GitRepository

internal abstract class GitMultipleCommitEditingOperation(protected val repository: GitRepository) {
  protected val project = repository.project

  protected fun rebase(
    commits: List<VcsCommitMetadata>,
    rebaseEditor: GitRebaseEditorHandler,
    preserveMerges: Boolean = false
  ): OperationResult {
    val base = commits.last().parents.first().asString()
    val params = GitRebaseParams.editCommits(
      repository.vcs.version,
      base,
      rebaseEditor,
      preserveMerges,
      GitRebaseParams.AutoSquashOption.DISABLE
    )
    val indicator = ProgressManager.getInstance().progressIndicator ?: EmptyProgressIndicator()
    val spec = GitRebaseSpec.forNewRebase(project, params, listOf(repository), indicator)
    val process = GitMultipleCommitEditingProcess(project, spec)
    process.rebase()
    return process.result
  }

  private class GitMultipleCommitEditingProcess(project: Project, spec: GitRebaseSpec) : GitRebaseProcess(project, spec, null) {
    var result = OperationResult.INCOMPLETE

    override fun notifySuccess() {
      result = OperationResult.COMPLETE
    }
  }

  protected enum class OperationResult {
    COMPLETE,
    INCOMPLETE
  }
}