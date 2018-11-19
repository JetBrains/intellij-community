// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import git4idea.GitVcs
import git4idea.branch.GitRebaseParams
import git4idea.repo.GitRepository

class GitRebaseCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {

  override fun createVcsHandler(panel: CheckinProjectPanel): CheckinHandler {
    return object : CheckinHandler() {
      private var active: Boolean = false

      private lateinit var project: Project
      private lateinit var repository: GitRepository
      private lateinit var rebaseFrom: String

      override fun checkinSuccessful() {
        if (!active) return
        object : Task.Backgroundable(project, "Rebasing") {
          override fun run(indicator: ProgressIndicator) {
            val params = GitRebaseParams.editCommits(rebaseFrom, null, false)
            GitRebaseUtils.rebase(project, listOf(repository), params, indicator)
          }
        }.queue()
      }

      override fun acceptExecutor(executor: CommitExecutor?): Boolean {
        if (executor is GitAutoSquashCommitAction.GitRebaseAfterCommitExecutor) {
          active = true
          project = executor.project
          repository = executor.repository
          rebaseFrom = executor.hash
          return false
        }

        return true
      }
    }
  }
}