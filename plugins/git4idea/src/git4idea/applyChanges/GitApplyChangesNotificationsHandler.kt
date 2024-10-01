// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitApplyChangesNotification
import git4idea.GitDisposable
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.stash.GitChangesSaver
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper class for hiding or showing notifications related to applying changes
 * when [git4idea.GitApplyChangesProcess.execute] is already finished
 */
@Service(Service.Level.PROJECT)
internal class GitApplyChangesNotificationsHandler(private val project: Project) {
  private var shouldHideNotifications = AtomicBoolean()
  private var changesSaverAndOperation = AtomicReference<Pair<GitChangesSaver, @Nls String>?>()

  init {
    project.messageBus.connect(GitDisposable.Companion.getInstance(project))
      .subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener {
        if (!isCherryPickingOrReverting(it)) {
          if (shouldHideNotifications.compareAndSet(true, false)) {
            LOG.debug("Hiding notifications")
            GitApplyChangesNotification.Companion.expireAll<GitApplyChangesNotification.ExpireAfterRepoStateChanged>(project)
          }

          changesSaverAndOperation.getAndSet(null)?.let { (changesSaver, operation) ->
            LOG.debug("Suggesting to restore saved changes after $operation")
            showRestoreChangesNotification(changesSaver, operation)
          }
        }
      })
  }

  fun beforeApply() {
    shouldHideNotifications.set(false)
    changesSaverAndOperation.set(null)
  }

  fun operationFailed(operationName: @Nls String, repository: GitRepository, changesSaver: GitChangesSaver?) {
    val cherryPickingOrReverting = isCherryPickingOrReverting(repository)
    if (cherryPickingOrReverting) {
      shouldHideNotifications.set(true)
    }

    if (changesSaver != null) {
      if (cherryPickingOrReverting) {
        changesSaverAndOperation.set(changesSaver to operationName)
      }
      else {
        showRestoreChangesNotification(changesSaver, operationName)
      }
    }
  }

  private fun showRestoreChangesNotification(changesSaver: GitChangesSaver, operation: @Nls String) {
    VcsNotifier.getInstance(project).notify(
      GitApplyChangesCanRestoreNotification(project, changesSaver, operation)
    )
  }

  private fun isCherryPickingOrReverting(repository: GitRepository): Boolean =
    repository.state == Repository.State.GRAFTING || repository.state == Repository.State.REVERTING

  internal companion object {
    private val LOG = thisLogger()

    fun getInstance(project: Project): GitApplyChangesNotificationsHandler =
      project.getService(GitApplyChangesNotificationsHandler::class.java)
  }
}