// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.ui.DvcsBundle
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.messages.MessagesService
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.push.*
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.validators.GitRefNameValidator
import org.jetbrains.annotations.Nls
import java.util.concurrent.CompletableFuture

object GitPushUtil {
  @RequiresEdt
  @JvmStatic
  fun findOrPushRemoteBranch(project: Project,
                             progressIndicator: ProgressIndicator,
                             repository: GitRepository,
                             remote: GitRemote,
                             localBranch: GitLocalBranch,
                             dialogMessages: BranchNameInputDialogMessages): CompletableFuture<GitRemoteBranch> {

    val gitPushSupport = DvcsUtil.getPushSupport(GitVcs.getInstance(project)) as? GitPushSupport
                         ?: return CompletableFuture.failedFuture(ProcessCanceledException())

    val existingPushTarget = findPushTarget(repository, remote, localBranch)
    if (existingPushTarget != null) {
      val localHash = repository.branches.getHash(localBranch)
      val remoteHash = repository.branches.getHash(existingPushTarget.branch)
      if (localHash == remoteHash) return CompletableFuture.completedFuture(existingPushTarget.branch)
    }

    val pushTarget = existingPushTarget
                     ?: inputPushTarget(repository, remote, localBranch, dialogMessages)
                     ?: return CompletableFuture.failedFuture(ProcessCanceledException())

    val future = CompletableFuture<GitRemoteBranch>()
    ProgressManager.getInstance().runProcessWithProgressAsynchronously(
      object : Task.Backgroundable(repository.project, DvcsBundle.message("push.process.pushing"), true) {

        override fun run(indicator: ProgressIndicator) {
          indicator.text = DvcsBundle.message("push.process.pushing")
          val pushSpec = PushSpec(GitPushSource.create(localBranch), pushTarget)
          val pushResult = GitPushOperation(repository.project, gitPushSupport, mapOf(repository to pushSpec), null, false, false)
                             .execute().results[repository] ?: error("Missing push result")
          check(pushResult.error == null) {
            GitBundle.message("push.failed.error.message", pushResult.error.orEmpty())
          }
        }

        override fun onSuccess() {
          future.complete(pushTarget.branch)
        }

        override fun onThrowable(error: Throwable) {
          future.completeExceptionally(error)
        }

        override fun onCancel() {
          future.completeExceptionally(ProcessCanceledException())
        }
      }, progressIndicator)
    return future
  }

  @RequiresEdt
  private fun inputPushTarget(repository: GitRepository,
                              remote: GitRemote,
                              localBranch: GitLocalBranch,
                              dialogMessages: BranchNameInputDialogMessages): GitPushTarget? {
    val branchName = MessagesService.getInstance().showInputDialog(repository.project, null,
                                                                   dialogMessages.inputMessage,
                                                                   dialogMessages.title,
                                                                   null, localBranch.name, null, null,
                                                                   dialogMessages.inputComment)
                     ?: return null
    //always set tracking
    return GitPushTarget(GitStandardRemoteBranch(remote, GitRefNameValidator.getInstance().cleanUpBranchName(branchName)), true, GitPushTargetType.TRACKING_BRANCH)
  }

  data class BranchNameInputDialogMessages(
    @NlsContexts.DialogTitle val title: String,
    @NlsContexts.DialogMessage val inputMessage: String,
    @Nls(capitalization = Nls.Capitalization.Sentence) val inputComment: String
  )

  @JvmStatic
  fun findPushTarget(repository: GitRepository, remote: GitRemote, branch: GitLocalBranch): GitPushTarget? {
    return GitPushTarget.getFromPushSpec(repository, remote, branch)
           ?: GitBranchUtil.getTrackInfoForBranch(repository, branch)
             ?.takeIf { it.remote == remote }
             // use tracking information only for branches with the same local and remote name
             ?.takeIf { it.remoteBranch.nameForRemoteOperations == branch.name }
             ?.let { GitPushTarget(it.remoteBranch, false, GitPushTargetType.TRACKING_BRANCH) }
  }
}

