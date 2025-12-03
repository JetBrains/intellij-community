// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.components.service
import git4idea.i18n.GitBundle
import kotlinx.coroutines.CoroutineScope

internal class GitInteractiveRebaseAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.rebase.name")
  )

  override fun actionPerformedAfterChecks(scope: CoroutineScope, commitEditingData: SingleCommitEditingData) {
    commitEditingData.project.service<GitInteractiveRebaseService>()
      .launchRebase(commitEditingData.repository, commitEditingData.selectedCommit, commitEditingData.logData)
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.interactive.action.failure.title")
}