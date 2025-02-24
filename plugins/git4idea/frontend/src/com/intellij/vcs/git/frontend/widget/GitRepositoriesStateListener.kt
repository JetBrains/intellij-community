// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.widget

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.asEntityOrNull
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryFavoriteRefsEntity
import com.intellij.vcs.git.shared.rhizome.repository.GitRepositoryStateEntity
import com.jetbrains.rhizomedb.EID
import fleet.kernel.rete.each
import fleet.kernel.rete.filter
import fleet.kernel.rete.launchOnEach
import fleet.kernel.rete.tokenSetsFlow
import fleet.multiplatform.shims.ConcurrentHashMap
import fleet.multiplatform.shims.ConcurrentHashSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class GitRepositoriesStateListener(private val project: Project, cs: CoroutineScope) {
  private val repoEidToRpcId = ConcurrentHashMap<EID, RepositoryId>()
  private val reposInProject = ConcurrentHashSet<RepositoryId>()

  private val widgetUpdateFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    cs.launch {
      val projectEntity = project.asEntityOrNull() ?: return@launch
      GitRepositoryEntity.each().filter { repoEntity -> repoEntity.project == projectEntity }.tokenSetsFlow().collect { tokenSet ->
        supervisorScope {
          tokenSet.asserted.forEach { repoEntityMatch ->
            repoEidToRpcId[repoEntityMatch.value.eid] = repoEntityMatch.value.repositoryId
            reposInProject.add(repoEntityMatch.value.repositoryId)
          }
          tokenSet.retracted.forEach { repoEntityMatch ->
            val repoId = repoEidToRpcId.remove(repoEntityMatch.value.eid)
            if (repoId != null) {
              reposInProject.remove(repoId)
            }
          }
          notifyWidget()
        }
      }

      GitRepositoryStateEntity.each().filter { stateEntity -> reposInProject.contains(stateEntity.repositoryId) }.launchOnEach {
        notifyWidget()
      }

      GitRepositoryFavoriteRefsEntity.each().filter { stateEntity -> reposInProject.contains(stateEntity.repositoryId) }.launchOnEach {
        notifyWidget()
      }
    }

    cs.launch {
      @OptIn(FlowPreview::class)
      widgetUpdateFlow.debounce(100.milliseconds).collect {
        project.messageBus.syncPublisher(GitWidgetUpdateListener.TOPIC).triggerUpdate()
      }
    }
  }

  private fun notifyWidget() {
    if (project.isDisposed) return
    widgetUpdateFlow.tryEmit(Unit)
  }

  companion object {
    fun getInstance(project: Project): GitRepositoriesStateListener = project.service()
  }
}