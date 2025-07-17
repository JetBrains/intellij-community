// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.dvcs.repo.VcsRepositoryMappingListener
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.repo.GitRepositoryColor
import com.intellij.vcs.git.repo.GitRepositoryColorsState
import com.intellij.vcs.git.rpc.GitRepositoryColorsApi
import com.intellij.vcs.log.ui.VcsLogColorManagerFactory
import git4idea.GitDisposable
import git4idea.repo.GitRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds


internal class GitRepositoryColorsApiImpl : GitRepositoryColorsApi {

  @OptIn(FlowPreview::class)
  override suspend fun syncColors(projectId: ProjectId): Flow<GitRepositoryColorsState> {
    requireOwner()

    val project = projectId.findProjectOrNull() ?: return emptyFlow()

    return callbackFlow {
      val notifier = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

      val messageBusConnection = readAction {
        if (project.isDisposed) {
          close()
          return@readAction null
        }

        val coroutineScope = GitDisposable.getInstance(project).coroutineScope
        coroutineScope.launch {
          send(calcColorsState(project))
          notifier.debounce(100.milliseconds).collectLatest {
            LOG.debug("Sending new colors")
            send(calcColorsState(project))
          }
        }

        project.messageBus.connect(coroutineScope).also {
          it.subscribe(VcsRepositoryManager.VCS_REPOSITORY_MAPPING_UPDATED, VcsRepositoryMappingListener {
            LOG.debug("VCS mapping changed")
            notifier.tryEmit(Unit)
          })
          it.subscribe(LafManagerListener.TOPIC, LafManagerListener {
            LOG.debug("LAF changed")
            notifier.tryEmit(Unit)
          })
        }
      }
      awaitClose {
        LOG.debug("Connection closed")
        messageBusConnection?.disconnect()
      }
    }
  }

  private fun calcColorsState(project: Project): GitRepositoryColorsState {
    val allRepos = VcsRepositoryManager.getInstance(project).getRepositories()

    val colorManager = VcsLogColorManagerFactory.create(allRepos.map { it.root }.toSet())
    val gitRepositories = allRepos.filterIsInstance<GitRepository>()
    val repositoryColors: MutableMap<RepositoryId, GitRepositoryColor> = mutableMapOf()

    for (gitRepository in gitRepositories) {
      val color = colorManager.getRootColor(gitRepository.root)
      repositoryColors[gitRepository.rpcId] = GitRepositoryColor.of(color)
    }

    return GitRepositoryColorsState(repositoryColors)
  }

  companion object {
    private val LOG = Logger.getInstance(GitRepositoryColorsApiImpl::class.java)
  }
}