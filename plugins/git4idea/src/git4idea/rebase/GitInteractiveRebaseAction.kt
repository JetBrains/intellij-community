// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.components.service
import git4idea.i18n.GitBundle

internal class GitInteractiveRebaseAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.rebase.name")
  )

  override fun actionPerformedAfterChecks(commitEditingData: SingleCommitEditingData) {
    commitEditingData.project.service<GitInteractiveRebaseService>()
      .launchRebase(commitEditingData.repository, commitEditingData.selectedCommit, commitEditingData.logData)
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.interactive.action.failure.title")
}