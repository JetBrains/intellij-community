// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.isHead
import com.intellij.openapi.components.service
import com.intellij.vcs.log.util.VcsUserUtil.getShortPresentation
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitNewCommitMessageActionDialog
import git4idea.rebase.log.getOrLoadDetails
import kotlinx.coroutines.CoroutineScope

internal class GitRewordAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.reword.name")
  )

  override fun checkNotMergeCommit(commitEditingData: SingleCommitEditingData): String? {
    val commit = commitEditingData.selectedCommit
    val repository = commitEditingData.repository
    if (repository.isHead(commit.id)) {
      // allow amending merge commit
      return null
    }

    return super.checkNotMergeCommit(commitEditingData)
  }

  override fun actionPerformedAfterChecks(scope: CoroutineScope, commitEditingData: SingleCommitEditingData) {
    val details = getOrLoadDetails(commitEditingData.project, commitEditingData.logData, commitEditingData.selection)
    val commit = details.first()
    val dialog = GitNewCommitMessageActionDialog(
      commitEditingData,
      originMessage = commit.fullMessage,
      title = GitBundle.message("rebase.log.reword.dialog.title"),
      dialogLabel = GitBundle.message(
        "rebase.log.reword.dialog.description.label",
        1,
        commit.id.toShortString(),
        getShortPresentation(commit.author)
      )
    )
    dialog.show { newMessage ->
      commitEditingData.repository.project.service<GitRewordService>()
        .launchReword(commitEditingData.repository, commit, newMessage)
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.reword.action.failure.title")

  override fun getProhibitedStateMessage(commitEditingData: SingleCommitEditingData, operation: String): String? {
    if (commitEditingData.repository.state == Repository.State.REBASING && commitEditingData.isHeadCommit) {
      return null
    }
    return super.getProhibitedStateMessage(commitEditingData, operation)
  }
}