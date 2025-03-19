// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.repo.Repository
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.rpcId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.rpc.GitWidgetApi
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.config.GitVcsSettings
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryStateChangeListener
import git4idea.ui.branch.GitCurrentBranchPresenter
import git4idea.ui.toolbar.GitToolbarWidgetAction
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal class GitWidgetApiImpl : GitWidgetApi {
  override suspend fun getWidgetState(projectId: ProjectId, selectedFile: VirtualFileId?): Flow<GitWidgetState> {
    val project = projectId.findProject()
    val file = selectedFile?.virtualFile()

    return callbackFlow {
      fun trySendNewState() {
        val widgetState = GitToolbarWidgetAction.getWidgetState(project, file)
        if (widgetState is GitToolbarWidgetAction.GitWidgetState.Repo) {
          GitVcsSettings.getInstance(project).setRecentRoot(widgetState.repository.root.path)
        }

        trySend(widgetState.toRpc())
      }

      val connection = project.messageBus.connect()
      connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener(::trySendNewState))
      connection.subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
        override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) = trySendNewState()
      })
      connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener(::trySendNewState))

      trySendNewState()
      awaitClose { connection.disconnect() }
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
      repository = rpcId(),
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
}

private fun Repository.rpcId(): RepositoryId = RepositoryId(projectId = project.projectId(), rootPath = root.rpcId())