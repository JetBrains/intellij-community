// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.project.ProjectId
import com.intellij.vcs.git.branch.GitInOutProjectState
import com.intellij.vcs.git.rpc.GitIncomingOutgoingStateApi
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import git4idea.branch.GitBranchIncomingOutgoingManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class GitIncomingOutgoingStateApiImpl : GitIncomingOutgoingStateApi {
  @OptIn(FlowPreview::class)
  override suspend fun syncState(projectId: ProjectId): Flow<GitInOutProjectState> =
    projectScopedCallbackFlow(projectId) { project, messageBusConnection ->
      requireOwner()
      val notifier = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

      val inOutManager = GitBranchIncomingOutgoingManager.getInstance(project)
      send(inOutManager.state)
      launch {
        notifier.debounce(IN_OUT_SYNC_DEBOUNCE).collectLatest {
          val state = inOutManager.state
          LOG.debug("Sending new state")
          send(state)
        }
      }

      messageBusConnection.subscribe(
        GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED,
        GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener {
          LOG.debug("Git incoming outgoing state changed")
          notifier.tryEmit(Unit)
        }
      )
    }

  companion object {
    private val LOG = Logger.getInstance(GitIncomingOutgoingStateApiImpl::class.java)

    private val IN_OUT_SYNC_DEBOUNCE = 100.milliseconds
  }
}