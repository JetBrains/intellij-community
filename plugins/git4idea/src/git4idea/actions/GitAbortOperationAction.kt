// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.actions

import com.intellij.CommonBundle
import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.DialogManager
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.util.GitFreezingProcess

internal abstract class GitAbortOperationAction(repositoryState: Repository.State,
                                                final override val operationName: String,
                                                private val gitCommand: GitCommand)
  : GitOperationActionBase(repositoryState) {

  private val operationNameCapitalised = StringUtil.capitalizeWords(operationName, true)

  class Merge : GitAbortOperationAction(Repository.State.MERGING, "merge", GitCommand.MERGE)
  class CherryPick : GitAbortOperationAction(Repository.State.GRAFTING, "cherry-pick", GitCommand.CHERRY_PICK)
  class Revert : GitAbortOperationAction(Repository.State.REVERTING, "revert", GitCommand.REVERT)

  override fun performInBackground(repository: GitRepository) {
    if (!confirmAbort(repository)) return

    runBackgroundableTask("Aborting $operationNameCapitalised Process", repository.project) { indicator ->
      doAbort(repository, indicator)
    }
  }

  private fun confirmAbort(repository: GitRepository): Boolean {
    var title = "Abort $operationNameCapitalised"
    var message = "Abort $operationName" + GitUtil.mention(repository) + "?"
    if (Messages.canShowMacSheetPanel()) {
      title = message
      message = ""
    }
    return DialogManager.showOkCancelDialog(repository.project, message, title, "Abort", CommonBundle.getCancelButtonText(),
                                            Messages.getQuestionIcon()) == Messages.OK
  }

  private fun doAbort(repository: GitRepository, indicator: ProgressIndicator) {
    val project = repository.project
    GitFreezingProcess(project, "abort") {
      DvcsUtil.workingTreeChangeStarted(project, "Abort").use {
        indicator.text2 = "git ${gitCommand.name()} --abort" + GitUtil.mention(repository)

        val startHash = GitUtil.getHead(repository)
        val handler = GitLineHandler(project, repository.root, gitCommand);
        handler.addParameters("--abort");
        val result = Git.getInstance().runCommand(handler);

        if (!result.success()) {
          VcsNotifier.getInstance(project).notifyError("$operationNameCapitalised Abort Failed", result.errorOutputAsHtmlString)
        }
        else {
          VcsNotifier.getInstance(project).notifySuccess("$operationNameCapitalised Abort Succeeded")

          GitUtil.updateAndRefreshChangedVfs(repository, startHash)
        }
      }
    }.execute()
  }
}
