// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.vcs.git.branch.GitInOutProjectState
import com.intellij.vcs.git.rpc.GitIncomingOutgoingStateApi
import git4idea.GitDisposable
import git4idea.branch.GitBranchIncomingOutgoingManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class GitIncomingOutgoingStateApiImpl : GitIncomingOutgoingStateApi {
  @OptIn(FlowPreview::class)
  override suspend fun syncState(projectId: ProjectId): Flow<GitInOutProjectState> {
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
          val inOutManager = GitBranchIncomingOutgoingManager.getInstance(project)
          send(inOutManager.state)
          notifier.debounce(IN_OUT_SYNC_DEBOUNCE).collectLatest {
            val state = inOutManager.state
            LOG.debug("Sending new state")
            send(state)
          }
        }

        project.messageBus.connect(coroutineScope).also {
          it.subscribe(
            GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED,
            GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener {
              LOG.debug("Git incoming outgoing state changed")
              notifier.tryEmit(Unit)
            }
          )
        }
      }

      awaitClose {
        LOG.debug("Connection closed")
        messageBusConnection?.disconnect()
      }
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitIncomingOutgoingStateApiImpl::class.java)

    private val IN_OUT_SYNC_DEBOUNCE = 100.milliseconds
  }
}