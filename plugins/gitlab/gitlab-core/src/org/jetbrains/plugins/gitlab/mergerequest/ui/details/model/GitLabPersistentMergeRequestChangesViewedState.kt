// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.mergerequest.ui.details.model

import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.registry.Registry
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.changes.GitBranchComparisonResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import org.jetbrains.plugins.gitlab.api.GitLabServerPath
import org.jetbrains.plugins.gitlab.mergerequest.data.GitLabMergeRequest
import org.jetbrains.plugins.gitlab.mergerequest.data.findViewedStateSha
import java.time.Duration

@Service(Service.Level.PROJECT)
@State(name = "GitLabMergeRequestChangesViewedState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class GitLabPersistentMergeRequestChangesViewedState
  : SerializablePersistentStateComponent<GitLabPersistentMergeRequestChangesViewedState.State>(State()) {
  companion object {
    private const val STALE_TIMEOUT_KEY = "gitlab.viewed.state.stale.timeout"
  }

  private val stateOfState = MutableStateFlow(State())

  val updatesFlow: Flow<Unit> = stateOfState.map { }

  @Serializable
  data class State(
    val states: List<MRViewedState> = listOf()
  ) {
    val statesMap: Map<MRId, MRViewedState> by lazy {
      states.associateBy { it.id }
    }
  }

  @Serializable
  data class MRId(
    val serverPath: GitLabServerPath = GitLabServerPath(),
    val projectId: String = "",
    val iid: String = "",
  )

  /**
   * @param viewedFiles The key is the path of a file, the value is the SHA for which last file change was seen.
   *                    The SHA should be a commit hash in which the corresponding file was changed.
   */
  @Serializable
  data class MRViewedState(
    val id: MRId,
    val lastUpdated: Long = System.currentTimeMillis(),
    val viewedFiles: Map<String, String> = mapOf()
  )

  /**
   * Checks the viewed state of [change].
   * A change of a single commit is never viewed, because the viewed state describes only the cumulative diff.
   */
  fun isViewed(mergeRequest: GitLabMergeRequest, parsedChanges: GitBranchComparisonResult, change: RefComparisonChange): Boolean {
    val gitRepository = mergeRequest.gitRemote.repository
    val sha = parsedChanges.findViewedStateSha(gitRepository, change) ?: return false
    val relPath = VcsFileUtil.relativePath(gitRepository.root, change.filePath)

    return state.statesMap[mergeRequest.viewedStateId]?.let {
      it.viewedFiles[relPath] == sha
    } ?: false
  }

  /**
   * Sets the viewed state of [changes].
   * The method skips a change of a single commit, because the viewed state describes only the cumulative diff.
   */
  fun markViewed(
    mergeRequest: GitLabMergeRequest,
    parsedChanges: GitBranchComparisonResult,
    changes: Iterable<RefComparisonChange>,
    viewed: Boolean,
  ) {
    val gitRepository = mergeRequest.gitRemote.repository
    val relPathsAndShas = changes.mapNotNull { change ->
      parsedChanges.findViewedStateSha(gitRepository, change)?.let { sha ->
        VcsFileUtil.relativePath(gitRepository.root, change.filePath) to sha
      }
    }
    if (relPathsAndShas.isEmpty()) return
    updateStateOrCreateAndCleanup(mergeRequest.viewedStateId) { st ->
      if (viewed) {
        MRViewedState(id = st.id, viewedFiles = st.viewedFiles + relPathsAndShas)
      }
      else {
        MRViewedState(id = st.id, viewedFiles = st.viewedFiles - relPathsAndShas.map { it.first }.toSet())
      }
    }
  }

  override fun loadState(state: State) {
    super.loadState(state)
    stateOfState.value = state
  }

  private inline fun updateStateOrCreateAndCleanup(id: MRId, stateUpdater: (MRViewedState) -> MRViewedState) {
    stateOfState.value = updateState { st ->
      val statesWithoutCurrent = st.states.filterNot { it.id == id }
      val newState = stateUpdater(st.statesMap[id] ?: MRViewedState(id))
      val newStates = statesWithoutCurrent + listOf(newState)

      val staleBeforeTime = System.currentTimeMillis() - Duration.ofDays(Registry.intValue(STALE_TIMEOUT_KEY).toLong()).toMillis()
      st.copy(states = newStates.filterNot { it.lastUpdated < staleBeforeTime || it.viewedFiles.isEmpty() })
    }
  }

  private val GitLabMergeRequest.viewedStateId: MRId
    get() = MRId(serverPath, projectId, iid)
}