// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.cherrypick

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import git4idea.GitApplyChangesNotification
import git4idea.GitApplyChangesNotification.ExpireAfterRepoStateChanged
import git4idea.GitDisposable
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
internal class GitCherryPickNotificationsHandler(project: Project) {
  private val cherryPickingIn = ConcurrentHashMap.newKeySet<GitRepository>()

  init {
    project.messageBus.connect(GitDisposable.getInstance(project))
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        if (it.state == Repository.State.GRAFTING) {
          cherryPickingIn.add(it)
        }
        else if (cherryPickingIn.remove(it)) {
          GitApplyChangesNotification.expireAll<ExpireAfterRepoStateChanged>(project)
        }
      })
  }
}