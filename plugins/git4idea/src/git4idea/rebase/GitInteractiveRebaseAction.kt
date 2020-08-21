// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.util.registry.Registry
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.interactivelyRebaseUsingLog
import git4idea.rebase.interactive.startInteractiveRebase

internal class GitInteractiveRebaseAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.rebase.name")
  )

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    val commit = commitEditingData.selectedCommit
    val repository = commitEditingData.repository

    if (Registry.`is`("git.interactive.rebase.collect.entries.using.log")) {
      interactivelyRebaseUsingLog(repository, commit, commitEditingData.logData)
    }
    else {
      startInteractiveRebase(repository, commit)
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.interactive.action.failure.title")
}