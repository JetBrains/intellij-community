// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.vcs.git.shared.rpc.GitWidgetApi
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import git4idea.GitDisposable
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryStateChangeListener
import git4idea.ui.branch.GitCurrentBranchPresenter
import git4idea.ui.toolbar.GitToolbarWidgetAction
import kotlinx.coroutines.flow.Flow

internal class GitWidgetApiImpl : GitWidgetApi {
  override suspend fun getWidgetState(projectId: ProjectId, selectedFile: VirtualFileId?): Flow<GitWidgetState> {
    val project = projectId.findProject()
    val file = selectedFile?.virtualFile()
    val scope = GitDisposable.getInstance(project).childScope("Git widget update scope")

    return flowWithMessageBus(project, scope) { connection ->
      fun trySendNewState() {
        val widgetState = GitToolbarWidgetAction.getWidgetState(project, file)
        if (widgetState is GitToolbarWidgetAction.GitWidgetState.Repo) {
          GitVcsSettings.getInstance(project).setRecentRoot(widgetState.repository.root.path)
        }

        trySend(widgetState.toRpc())
      }

      connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener {
        LOG.debug("VCS mapping changed. Sending new value")
        trySendNewState()
      })
      connection.subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
        override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) {
          LOG.debug("Git repository state changed: $repository. Sending new value")
          trySendNewState()
        }
      })
      connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
        LOG.debug("Git incoming outgoing state changed. Sending new value")
        trySendNewState()
      })
      connection.subscribe(GitCurrentBranchPresenter.PRESENTATION_UPDATED, GitCurrentBranchPresenter.PresentationUpdatedListener {
        LOG.debug("Branch presentation for the widget updated. Sending new value")
        trySendNewState()
      })

      trySendNewState()
    }
  }

  private fun GitToolbarWidgetAction.GitWidgetState.toRpc(): GitWidgetState = when (this) {
    GitToolbarWidgetAction.GitWidgetState.NotActivated,
    GitToolbarWidgetAction.GitWidgetState.NotSupported,
    GitToolbarWidgetAction.GitWidgetState.OtherVcs,
      -> GitWidgetState.DoNotShow
    GitToolbarWidgetAction.GitWidgetState.NoVcs -> GitWidgetState.NoVcs
    GitToolbarWidgetAction.GitWidgetState.GitVcs -> GitWidgetState.UnknownGitRepository
    is GitToolbarWidgetAction.GitWidgetState.Repo -> repository.getWidgetState()
  }

  private fun GitRepository.getWidgetState(): GitWidgetState.OnRepository {
    // TODO make reactive
    val presentation = GitCurrentBranchPresenter.getPresentation(this)

    return GitWidgetState.OnRepository(
      repository = this.rpcId,
      presentationData = GitWidgetState.RepositoryPresentation(
        icon = presentation.icon?.rpcId(),
        text = presentation.text,
        description = presentation.description,
        syncStatus = GitWidgetState.BranchSyncStatus(
          incoming = presentation.syncStatus.incoming,
          outgoing = presentation.syncStatus.outgoing,
        )
      )
    )
  }

  companion object {
    private val LOG = Logger.getInstance(GitWidgetApiImpl::class.java)
  }
}
