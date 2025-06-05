// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.ide.vfs.virtualFile
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.vcs.git.shared.repo.GitRepositoriesHolder
import com.intellij.vcs.git.shared.rpc.GitWidgetApi
import com.intellij.vcs.git.shared.rpc.GitWidgetState
import git4idea.GitDisposable
import git4idea.GitVcs
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener
import git4idea.branch.GitBranchUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVcsSettings
import git4idea.config.GitVersion
import git4idea.repo.GitRepoInfo
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryIdCache
import git4idea.repo.GitRepositoryStateChangeListener
import git4idea.ui.branch.GitCurrentBranchPresenter
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

internal class GitWidgetApiImpl : GitWidgetApi {
  override suspend fun getWidgetState(projectId: ProjectId, selectedFile: VirtualFileId?): Flow<GitWidgetState> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    val file = selectedFile?.virtualFile()

    return callbackFlow {
      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }
        project.messageBus.connect(GitDisposable.getInstance(project).coroutineScope).also {
          it.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener {
            LOG.debug("VCS mapping changed. Sending new value")
            trySendNewState(project, file)
          })
          it.subscribe(GitRepository.GIT_REPO_STATE_CHANGE, object : GitRepositoryStateChangeListener {
            override fun repositoryChanged(repository: GitRepository, previousInfo: GitRepoInfo, info: GitRepoInfo) {
              LOG.debug("Git repository state changed: $repository. Sending new value")
              trySendNewState(project, file)
            }
          })
          it.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED, GitIncomingOutgoingListener {
            LOG.debug("Git incoming outgoing state changed. Sending new value")
            trySendNewState(project, file)
          })
          it.subscribe(GitCurrentBranchPresenter.PRESENTATION_UPDATED, GitCurrentBranchPresenter.PresentationUpdatedListener {
            LOG.debug("Branch presentation for the widget updated. Sending new value")
            trySendNewState(project, file)
          })
        }
      }

      awaitClose {
        LOG.debug("Connection closed")
        messageBusConnection?.disconnect()
      }
    }
  }

  fun ProducerScope<GitWidgetState>.trySendNewState(project: Project, file: VirtualFile?) {
    val widgetState = getWidgetState(project, file)
    if (widgetState is GitWidgetState.OnRepository) {
      val rootPath = GitRepositoryIdCache.getInstance(project).get(widgetState.repository)?.root
      if (rootPath != null) {
        GitVcsSettings.getInstance(project).setRecentRoot(rootPath.path)
      }
    }

    trySend(widgetState)
  }

  companion object {
    private val LOG = Logger.getInstance(GitWidgetApiImpl::class.java)

    @RequiresBackgroundThread
    fun getWidgetState(project: Project, selectedFile: VirtualFile?): GitWidgetState {
      val vcsManager = ProjectLevelVcsManager.getInstance(project)
      if (!vcsManager.areVcsesActivated() || !GitRepositoriesHolder.getInstance(project).initialized) return GitWidgetState.DoNotShow

      val gitRepository = GitBranchUtil.guessWidgetRepository(project, selectedFile)
      if (gitRepository != null) {
        val gitVersion = GitExecutableManager.getInstance().getVersion(project)
        return if (GitVersion.isUnsupportedWslVersion(gitVersion.type)) GitWidgetState.DoNotShow
        else gitRepository.getWidgetState()
      }

      val allVcss = vcsManager.allActiveVcss
      when {
        allVcss.isEmpty() -> return GitWidgetState.NoVcs(TrustedProjects.isProjectTrusted(project))
        allVcss.any { it.keyInstanceMethod == GitVcs.getKey() } -> return GitWidgetState.UnknownGitRepository
        else -> return GitWidgetState.DoNotShow
      }
    }

    private fun GitRepository.getWidgetState(): GitWidgetState.OnRepository {
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
  }
}
