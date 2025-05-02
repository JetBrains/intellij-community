// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.vcs.git.shared.branch.GitInOutProjectState
import com.intellij.vcs.git.shared.rpc.GitIncomingOutgoingStateApi
import git4idea.GitDisposable
import git4idea.branch.GitBranchIncomingOutgoingManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

internal class GitIncomingOutgoingStateApiImpl: GitIncomingOutgoingStateApi {
  @OptIn(FlowPreview::class)
  override suspend fun syncState(projectId: ProjectId): Flow<GitInOutProjectState> {
    val project = projectId.findProject()
    val scope = GitDisposable.getInstance(project).childScope("Git repository in/out synchronizer in ${project}")

    return flowWithMessageBus(project, scope) { connection ->
      val inOutManager = GitBranchIncomingOutgoingManager.getInstance(project)
      send(inOutManager.state)

      val notifier = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
      launch {
        notifier.debounce(IN_OUT_SYNC_DEBOUNCE).collectLatest {
          val state = inOutManager.state
          send(state)
        }
      }

      connection.subscribe(GitBranchIncomingOutgoingManager.GIT_INCOMING_OUTGOING_CHANGED,
                           GitBranchIncomingOutgoingManager.GitIncomingOutgoingListener { notifier.tryEmit(Unit) })
    }
  }

  companion object {
    private val IN_OUT_SYNC_DEBOUNCE = 100.milliseconds
  }
}