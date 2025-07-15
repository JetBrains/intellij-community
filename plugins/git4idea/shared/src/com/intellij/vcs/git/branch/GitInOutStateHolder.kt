// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import com.intellij.vcs.git.rpc.GitIncomingOutgoingStateApi
import git4idea.GitStandardLocalBranch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class GitInOutStateHolder(private val project: Project, cs: CoroutineScope) {
  private var state: GitInOutProjectState = GitInOutProjectState(emptyMap(), emptyMap())

  init {
    cs.launch {
      GitIncomingOutgoingStateApi.getInstance().syncState(project.projectId()).collect {
        LOG.debug("Received new state - in ${it.incoming.size} repos, out ${it.outgoing.size} repos")
        state = it
      }
    }
  }

  /**
   * @return incoming and outgoing counters for the local branch in the given repositories.
   *
   * Note that the state is synced with delay, so the value can be outdated
   */
  fun getState(branch: GitStandardLocalBranch, repositories: Collection<RepositoryId>): GitInOutCountersInProject {
    if (repositories.isEmpty()) return GitInOutCountersInProject.EMPTY

    val reposState = repositories.mapNotNull {
      val incoming = state.incoming[it]?.get(branch.name)
      val outgoing = state.outgoing[it]?.get(branch.name)
      if (incoming == null && outgoing == null) null
      else it to GitInOutCountersInRepo(incoming = incoming, outgoing = outgoing)
    }.toMap()

    return if (reposState.isEmpty()) GitInOutCountersInProject.EMPTY else GitInOutCountersInProject(reposState)
  }

  companion object {
    private val LOG = Logger.getInstance(GitInOutStateHolder::class.java)

    fun getInstance(project: Project): GitInOutStateHolder = project.service()
  }
}