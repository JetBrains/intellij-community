// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.applyChanges

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitApplyChangesNotification
import git4idea.GitDisposable
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryStateChangeListener
import git4idea.stash.GitChangesSaver
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper class for hiding or showing notifications related to applying changes
 * when [git4idea.GitApplyChangesProcess.execute] is already finished
 */
@Service(Service.Level.PROJECT)
internal class GitApplyChangesNotificationsHandler(private val project: Project) {
  private var changesSaver = AtomicReference<GitChangesSaver?>()

  init {
    project.messageBus.connect(GitDisposable.Companion.getInstance(project))
      .subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
        override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) {
          if (wasCherryPickingOrReverting(previousInfo, info)) {
            LOG.debug("Hiding notifications")
            GitApplyChangesNotification.Companion.expireAll<GitApplyChangesNotification.ExpireAfterRepoStateChanged>(project)

            changesSaver.getAndSet(null)?.let { changesSaver ->
              val operation = when (previousInfo.state) {
                Repository.State.GRAFTING -> GitBundle.message("cherry.pick.name")
                Repository.State.REVERTING -> GitBundle.message("revert.operation.name")
                else -> error("Unexpected state: ${previousInfo.state}")
              }
              LOG.debug("Suggesting to restore saved changes after $operation")
              showRestoreChangesNotification(changesSaver, operation)
            }
          }
        }

        private fun wasCherryPickingOrReverting(previousInfo: GitRepoInfo, info: GitRepoInfo): Boolean =
          isCherryPickingOrReverting(previousInfo.state) && !isCherryPickingOrReverting(info.state)
      })
  }

  fun beforeApply() {
    changesSaver.set(null)
  }

  fun operationFailed(operationName: @Nls String, repository: GitRepository, changesSaver: GitChangesSaver?) {
    if (changesSaver != null) {
      if (isCherryPickingOrReverting(repository.info.state)) {
        this.changesSaver.set(changesSaver)
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

  private fun isCherryPickingOrReverting(state: Repository.State): Boolean =
    state == Repository.State.GRAFTING || state == Repository.State.REVERTING

  internal companion object {
    private val LOG = thisLogger()

    fun getInstance(project: Project): GitApplyChangesNotificationsHandler =
      project.getService(GitApplyChangesNotificationsHandler::class.java)
  }
}