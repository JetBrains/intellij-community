// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import git4idea.GitVcs
import git4idea.repo.GitRepositoryManager

private val LOG = logger<GitCherryPickContinueCheckinHandlerFactory>()

internal class GitCherryPickContinueCheckinHandlerFactory : VcsCheckinHandlerFactory(GitVcs.getKey()) {
  override fun createVcsHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
    return object : CheckinHandler() {
      override fun checkinSuccessful() {
        if (!Registry.`is`("git.cherry.pick.use.git.sequencer")) return
        val repositoryManager = GitRepositoryManager.getInstance(panel.project)
        for (root in panel.roots) {
          val repository = repositoryManager.getRepositoryForRootQuick(root) ?: continue
          if (repository.state == Repository.State.GRAFTING) {
            LOG.debug("Detected commit during cherry-pick with sequencer, triggering continue")
            GitCherryPickContinueProcess.launchCherryPick(repository)
          }
        }
      }
    }
  }
}
