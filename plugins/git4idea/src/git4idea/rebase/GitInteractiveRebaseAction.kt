// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.util.registry.Registry
import git4idea.i18n.GitBundle
import git4idea.rebase.interactive.interactivelyRebaseUsingLog
import git4idea.rebase.interactive.startInteractiveRebase

class GitInteractiveRebaseAction : GitCommitEditingAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    prohibitRebaseDuringRebase(e, GitBundle.getString("rebase.log.action.operation.rebase.name"))
  }

  override fun actionPerformedAfterChecks(e: AnActionEvent) {
    val commit = getSelectedCommit(e)
    val repository = getRepository(e)

    if (Registry.`is`("git.interactive.rebase.collect.entries.using.log")) {
      interactivelyRebaseUsingLog(repository, commit, getLogData(e))
    }
    else {
      startInteractiveRebase(repository, commit)
    }
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.interactive.action.failure.title")
}