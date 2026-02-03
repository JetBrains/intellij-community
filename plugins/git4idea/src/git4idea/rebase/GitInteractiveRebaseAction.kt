// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.openapi.util.registry.Registry
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.interactivelyRebaseUsingLog
import git4idea.rebase.interactive.startInteractiveRebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class GitInteractiveRebaseAction : GitSingleCommitEditingAction() {
  override val prohibitRebaseDuringRebasePolicy = ProhibitRebaseDuringRebasePolicy.Prohibit(
    GitBundle.message("rebase.log.action.operation.rebase.name")
  )

  override fun actionPerformedAfterChecks(scope: CoroutineScope, commitEditingData: SingleCommitEditingData) {
    scope.launch {
      if (Registry.`is`("git.interactive.rebase.collect.entries.using.log")) {
        interactivelyRebaseUsingLog(commitEditingData.repository, commitEditingData.selectedCommit, commitEditingData.logData)
      }
      else {
        startInteractiveRebase(commitEditingData.repository, commitEditingData.selectedCommit)
      }
    }
  }

  override fun getFailureTitle(): String = GitBundle.message("rebase.log.interactive.action.failure.title")
}