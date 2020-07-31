// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.log.drop

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import git4idea.i18n.GitBundle
import git4idea.rebase.log.GitMultipleCommitEditingAction
import git4idea.rebase.log.getOrLoadDetails

internal class GitDropLogAction : GitMultipleCommitEditingAction() {
  override fun update(e: AnActionEvent, commitEditingData: MultipleCommitEditingData) {
    e.presentation.text = GitBundle.message("rebase.log.drop.action.custom.text", commitEditingData.selectedCommitList.size)
  }

  override fun actionPerformedAfterChecks(commitEditingData: MultipleCommitEditingData) {
    val project = commitEditingData.project
    val commitDetails = getOrLoadDetails(project, commitEditingData.logData, commitEditingData.selectedCommitList)
    object : Task.Backgroundable(project, GitBundle.getString("rebase.log.drop.progress.indicator.title")) {
      override fun run(indicator: ProgressIndicator) {
        GitDropOperation(commitEditingData.repository).execute(commitDetails)
      }
    }.queue()
  }

  override fun getFailureTitle(): String = GitBundle.getString("rebase.log.drop.action.failure.title")
}