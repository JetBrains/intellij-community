// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.util.PairConsumer
import git4idea.GitVcs
import git4idea.branch.GitRebaseParams
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository

class GitRebaseCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {

  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return object : CheckinHandler() {
      private var active: Boolean = false

      private lateinit var project: Project
      private lateinit var repository: GitRepository
      private lateinit var rebaseFrom: GitRebaseParams.RebaseUpstream

      override fun checkinSuccessful() {
        if (!active) return
        object : Task.Backgroundable(project, GitBundle.message("rebase.progress.indicator.title")) {
          override fun run(indicator: ProgressIndicator) {
            val params = GitRebaseParams.editCommits(repository.vcs.version, rebaseFrom, null, false)
            GitRebaseUtils.rebase(project, listOf(repository), params, indicator)
          }
        }.queue()
      }

      override fun beforeCheckin(executor: CommitExecutor?, additionalDataConsumer: PairConsumer<Any, Any>?): ReturnResult {
        executor as GitAutoSquashCommitAction.GitRebaseAfterCommitExecutor

        active = true
        project = executor.project
        repository = executor.repository
        rebaseFrom = executor.upstream

        return ReturnResult.COMMIT
      }

      override fun acceptExecutor(executor: CommitExecutor?): Boolean {
        return executor is GitAutoSquashCommitAction.GitRebaseAfterCommitExecutor
      }
    }
  }
}